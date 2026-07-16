package link.e4all;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import link.e4all.dialtone.DialtoneChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class VoiceChatBridgeHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4all-voicebridge");

    private final boolean isServerSide;
    private VoiceChatBridge.HostUdpRelay hostRelay;
    private VoiceChatBridge.ClientUdpProxy clientProxy;
    private boolean bridgeActive = false;

    public VoiceChatBridgeHandler(boolean isServerSide) {
        this.isServerSide = isServerSide;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if (isServerSide && isTunneledConnection(ctx.channel())
                && VoiceChatBridge.getVoiceChatPort() > 0
                && VoiceChatPacketHelper.isCustomPayloadPacket(msg)) {
            String channel = VoiceChatPacketHelper.getPayloadChannel(msg);
            if (VoiceChatPacketHelper.SVC_SECRET_CHANNEL.equals(channel)) {
                handleOutgoingSecretPacket(ctx, msg, promise);
                return;
            }
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (VoiceChatPacketHelper.isCustomPayloadPacket(msg)) {
            String channel = VoiceChatPacketHelper.getPayloadChannel(msg);

            if (!isServerSide && VoiceChatPacketHelper.SVC_SECRET_CHANNEL.equals(channel)) {
                handleIncomingSecretPacket(ctx, msg);
                return;
            }
        }
        super.channelRead(ctx, msg);
    }

    private boolean isTunneledConnection(Channel channel) {
        return channel.isActive()
                && (channel instanceof DialtoneChannel || channel instanceof QuicStreamChannel);
    }

    private Object getPayloadObject(Object msg) {
        for (String methodName : new String[]{"payload", "getPayload"}) {
            try {
                Method m = msg.getClass().getMethod(methodName);
                Object payload = m.invoke(msg);
                if (payload != null) return payload;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private int getPortViaReflection(Object payload) {

        try {
            Method m = payload.getClass().getMethod("getServerPort");
            return (int) m.invoke(payload);
        } catch (Exception ignored) {}

        try {
            Field f = payload.getClass().getDeclaredField("serverPort");
            f.setAccessible(true);
            return f.getInt(payload);
        } catch (Exception ignored) {}
        return -1;
    }

    private String getVoiceHostViaReflection(Object payload) {

        try {
            Method m = payload.getClass().getMethod("getVoiceHost");
            return (String) m.invoke(payload);
        } catch (Exception ignored) {}

        try {
            Field f = payload.getClass().getDeclaredField("voiceHost");
            f.setAccessible(true);
            return (String) f.get(payload);
        } catch (Exception ignored) {}
        return null;
    }

    private boolean modifyPayloadInPlace(Object payload, int newPort, String newHost) {
        try {
            Field portField = payload.getClass().getDeclaredField("serverPort");
            Field hostField = payload.getClass().getDeclaredField("voiceHost");

            try {
                Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

                unsafe.putInt(payload, unsafe.objectFieldOffset(portField), newPort);
                unsafe.putObject(payload, unsafe.objectFieldOffset(hostField), newHost);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Unsafe modification failed, trying setAccessible", e);
            }

            portField.setAccessible(true);
            hostField.setAccessible(true);
            portField.setInt(payload, newPort);
            hostField.set(payload, newHost);
            return true;
        } catch (Exception e) {
            LOGGER.debug("Failed to modify SecretPacket payload in-place", e);
            return false;
        }
    }

    private void handleOutgoingSecretPacket(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        Object payload = getPayloadObject(msg);
        if (payload != null) {
            int svcPort = getPortViaReflection(payload);
            if (svcPort > 0) {

                try {
                    if (hostRelay != null) {
                        hostRelay.close();
                    }
                    hostRelay = new VoiceChatBridge.HostUdpRelay(svcPort, ctx.channel());
                    bridgeActive = true;
                    LOGGER.info("Started voice chat bridge for tunneled player (SVC port: {})", svcPort);
                } catch (SocketException e) {
                    LOGGER.error("Failed to start voice chat UDP relay", e);
                    super.write(ctx, msg, promise);
                    return;
                }

                if (modifyPayloadInPlace(payload, svcPort, "e4all-vc-bridge")) {
                    LOGGER.debug("Modified SecretPacket in-place via reflection (host→client)");
                    super.write(ctx, msg, promise);
                    return;
                }

                LOGGER.debug("In-place modification failed, trying byte-level approach");
            }
        }

        byte[] data = VoiceChatPacketHelper.getPayloadData(msg);
        if (data == null) {
            LOGGER.warn("Could not read SVC SecretPacket data (both reflection and byte extraction failed), passing through");
            super.write(ctx, msg, promise);
            return;
        }

        int svcPort = VoiceChatPacketHelper.readSecretPacketPort(data);
        if (svcPort <= 0) {
            LOGGER.warn("Invalid SVC port in SecretPacket: {}, passing through", svcPort);
            super.write(ctx, msg, promise);
            return;
        }

        if (!bridgeActive) {
            try {
                if (hostRelay != null) {
                    hostRelay.close();
                }
                hostRelay = new VoiceChatBridge.HostUdpRelay(svcPort, ctx.channel());
                bridgeActive = true;
                LOGGER.info("Started voice chat bridge for tunneled player (SVC port: {})", svcPort);
            } catch (SocketException e) {
                LOGGER.error("Failed to start voice chat UDP relay", e);
                super.write(ctx, msg, promise);
                return;
            }
        }

        byte[] rewritten = VoiceChatPacketHelper.rewriteSecretPacket(data, svcPort, "e4all-vc-bridge");
        if (rewritten != null) {
            sendModifiedSecretPacket(ctx, rewritten, msg, promise);
        } else {
            LOGGER.warn("Failed to rewrite SecretPacket, passing through unchanged");
            super.write(ctx, msg, promise);
        }
    }

    private void sendModifiedSecretPacket(ChannelHandlerContext ctx, byte[] newData, Object originalPacket, ChannelPromise promise) throws Exception {
        Object packet = buildSecretPacket(newData, originalPacket);
        if (packet != null) {
            ctx.write(packet, promise);
        } else {
            LOGGER.warn("Could not reconstruct SecretPacket, passing original");
            ctx.write(originalPacket, promise);
        }
    }

    private void handleIncomingSecretPacket(ChannelHandlerContext ctx, Object msg) throws Exception {

        Object payload = getPayloadObject(msg);
        if (payload != null) {
            String voiceHost = getVoiceHostViaReflection(payload);
            if (voiceHost != null) {
                if (!"e4all-vc-bridge".equals(voiceHost)) {

                    ctx.fireChannelRead(msg);
                    return;
                }

                try {
                    if (clientProxy != null) {
                        clientProxy.close();
                    }
                    clientProxy = new VoiceChatBridge.ClientUdpProxy(ctx.channel());
                    int proxyPort = clientProxy.getLocalPort();
                    bridgeActive = true;
                    LOGGER.info("Voice chat bridge active — SVC client will use local proxy on port {}", proxyPort);

                    if (modifyPayloadInPlace(payload, proxyPort, "127.0.0.1")) {
                        LOGGER.debug("Modified SecretPacket in-place via reflection (server→client)");
                        ctx.fireChannelRead(msg);
                        return;
                    }

                    LOGGER.debug("In-place modification failed, trying byte-level approach");
                } catch (SocketException e) {
                    LOGGER.error("Failed to start voice chat client proxy", e);
                    ctx.fireChannelRead(msg);
                    return;
                }
            }
        }

        byte[] data = VoiceChatPacketHelper.getPayloadData(msg);
        if (data == null) {
            LOGGER.debug("Could not read incoming SecretPacket data (both reflection and byte extraction failed), passing through");
            ctx.fireChannelRead(msg);
            return;
        }

        String voiceHost = readVoiceHostFromSecretPacket(data);
        if (!"e4all-vc-bridge".equals(voiceHost)) {

            ctx.fireChannelRead(msg);
            return;
        }

        try {
            if (clientProxy == null || !bridgeActive) {
                if (clientProxy != null) {
                    clientProxy.close();
                }
                clientProxy = new VoiceChatBridge.ClientUdpProxy(ctx.channel());
                bridgeActive = true;
                LOGGER.info("Voice chat bridge active — SVC client will use local proxy on port {}", clientProxy.getLocalPort());
            }
            int proxyPort = clientProxy.getLocalPort();

            byte[] rewritten = VoiceChatPacketHelper.rewriteSecretPacket(data, proxyPort, "127.0.0.1");
            if (rewritten != null) {
                Object packet = buildSecretPacket(rewritten, msg);
                if (packet != null) {
                    ctx.fireChannelRead(packet);
                } else {
                    LOGGER.warn("Could not reconstruct SecretPacket for client, passing original");
                    ctx.fireChannelRead(msg);
                }
            } else {
                LOGGER.warn("Failed to rewrite SecretPacket for client, passing original");
                ctx.fireChannelRead(msg);
            }
        } catch (SocketException e) {
            LOGGER.error("Failed to start voice chat client proxy", e);
            ctx.fireChannelRead(msg);
        }
    }

    private Object buildSecretPacket(byte[] newData, Object originalPacket) {
        VoiceChatPacketHelper.initReflection();
        ByteBuf rawBuf = null;
        Object friendlyBuf = null;
        try {
            rawBuf = Unpooled.wrappedBuffer(newData);
            Class<?> friendlyBufClass = Class.forName(VoiceChatPacketHelper.findFriendlyByteBufClassName());
            friendlyBuf = friendlyBufClass.getConstructor(ByteBuf.class).newInstance(rawBuf);

            try {
                Object payload = getPayloadObject(originalPacket);

                if (payload != null) {
                    Object newPayload = null;

                    for (java.lang.reflect.Constructor<?> ctor : payload.getClass().getConstructors()) {
                        Class<?>[] params = ctor.getParameterTypes();
                        if (params.length == 1 && params[0].isAssignableFrom(friendlyBufClass)) {
                            newPayload = ctor.newInstance(friendlyBuf);
                            break;
                        }
                    }

                    if (newPayload == null) {
                        try {
                            java.lang.reflect.Constructor<?> noArgCtor = payload.getClass().getDeclaredConstructor();
                            noArgCtor.setAccessible(true);
                            Object tempPayload = noArgCtor.newInstance();
                            for (String readMethod : new String[]{"fromBytes", "read"}) {
                                try {
                                    Method m = payload.getClass().getMethod(readMethod, friendlyBufClass);
                                    Object result = m.invoke(tempPayload, friendlyBuf);

                                    newPayload = (result != null) ? result : tempPayload;
                                    break;
                                } catch (NoSuchMethodException ignored) {}
                                try {
                                    Method m = payload.getClass().getMethod(readMethod, io.netty.buffer.ByteBuf.class);
                                    Object result = m.invoke(tempPayload, friendlyBuf);
                                    newPayload = (result != null) ? result : tempPayload;
                                    break;
                                } catch (NoSuchMethodException ignored) {}
                            }
                        } catch (NoSuchMethodException ignored) {

                        }
                    }

                    if (newPayload != null) {
                        for (java.lang.reflect.Constructor<?> ctor : originalPacket.getClass().getConstructors()) {
                            Class<?>[] params = ctor.getParameterTypes();
                            if (params.length == 1 && params[0].isAssignableFrom(newPayload.getClass())) {
                                Object packet = ctor.newInstance(newPayload);
                                rawBuf = null;
                                friendlyBuf = null;
                                return packet;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("1.20.2+ payload reconstruction failed, trying fallback", e);
            }

            Object rl = VoiceChatPacketHelper.makeResourceLocation("voicechat", "secret");
            Class<?> packetClass = VoiceChatPacketHelper.findS2CPayloadClass();
            for (java.lang.reflect.Constructor<?> ctor : packetClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 2) {
                    try {
                        Object packet = ctor.newInstance(rl, friendlyBuf);
                        rawBuf = null;
                        friendlyBuf = null;
                        return packet;
                    } catch (Exception ignored) {}
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to build SecretPacket", e);
            return null;
        } finally {
            if (friendlyBuf instanceof ByteBuf buf) {
                buf.release();
            } else if (rawBuf != null) {
                rawBuf.release();
            }
        }
    }

    public void handleRawVoiceData(byte[] data) {
        if (isServerSide && hostRelay != null) {
            hostRelay.onClientData(data);
        } else if (!isServerSide && clientProxy != null) {
            clientProxy.onServerData(data);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.handlerRemoved(ctx);
    }

    private void cleanup() {
        if (hostRelay != null) {
            hostRelay.close();
            hostRelay = null;
        }
        if (clientProxy != null) {
            clientProxy.close();
            clientProxy = null;
        }
        bridgeActive = false;
    }

    private String readVoiceHostFromSecretPacket(byte[] data) {
        if (data.length < 55) return null;
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        try {
            buf.skipBytes(54); 
            int len = VoiceChatPacketHelper.readVarInt(buf);
            if (len <= 0 || len > buf.readableBytes()) return null;
            byte[] hostBytes = new byte[len];
            buf.readBytes(hostBytes);
            return new String(hostBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            buf.release();
        }
    }
}