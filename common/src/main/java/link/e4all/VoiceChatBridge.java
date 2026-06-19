package link.e4all;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class VoiceChatBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4all-voicebridge");
    private static volatile int cachedVoiceChatPort = -2;
    public static int getVoiceChatPort() {
        if (cachedVoiceChatPort != -2) return cachedVoiceChatPort;
        try {
            Class<?> voicechatClass = Class.forName("de.maxhenkel.voicechat.Voicechat");
            Object serverVoiceEvents = voicechatClass.getField("SERVER").get(null);
            if (serverVoiceEvents == null) {
                LOGGER.debug("SVC SERVER field is null (not ready yet), will retry");
                return -1;
            }
            Object server = serverVoiceEvents.getClass().getMethod("getServer").invoke(serverVoiceEvents);
            if (server == null) {
                LOGGER.debug("SVC server object is null (not ready yet), will retry");
                return -1;
            }
            int port = (int) server.getClass().getMethod("getPort").invoke(server);
            if (port <= 0) {
                LOGGER.debug("SVC voice server port is {} (not ready yet), will retry", port);
                return -1;
            }
            cachedVoiceChatPort = port;
            LOGGER.info("Detected Simple Voice Chat server on UDP port {}", port);
            return port;
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Simple Voice Chat not installed");
            cachedVoiceChatPort = -1;
            return -1;
        } catch (Exception e) {
            LOGGER.debug("Failed to detect Simple Voice Chat port (will retry)", e);
            return -1;
        }
    }
    public static void resetCachedPort() {
        cachedVoiceChatPort = -2;
    }
    public static class HostUdpRelay {
        private final DatagramSocket socket;
        private final InetSocketAddress svcServerAddr;
        private final Channel minecraftChannel;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread receiveThread;
        public HostUdpRelay(int svcPort, Channel minecraftChannel) throws SocketException {
            this.socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
            this.socket.setSoTimeout(1000);
            this.svcServerAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), svcPort);
            this.minecraftChannel = minecraftChannel;
            this.receiveThread = new Thread(this::receiveLoop, "e4all-vc-host-relay");
            this.receiveThread.setDaemon(true);
            this.receiveThread.start();
            LOGGER.debug("Started host UDP relay on port {} -> SVC port {}", socket.getLocalPort(), svcPort);
        }
        public void onClientData(byte[] data) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length, svcServerAddr);
                socket.send(packet);
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.warn("Failed to forward voice data to SVC server", e);
                }
            }
        }
        private void receiveLoop() {
            byte[] buf = new byte[4096];
            while (running.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    if (!running.get() || !minecraftChannel.isActive()) break;
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    VoiceChatPacketHelper.sendVoiceData(minecraftChannel, data, true);
                } catch (SocketTimeoutException ignored) {
                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.debug("UDP receive error in host relay", e);
                    }
                }
            }
        }
        public void close() {
            running.set(false);
            socket.close();
            LOGGER.debug("Closed host UDP relay");
        }
    }
    public static class ClientUdpProxy {
        private final DatagramSocket socket;
        private final Channel minecraftChannel;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread receiveThread;
        private volatile SocketAddress svcClientAddr; 
        public ClientUdpProxy(Channel minecraftChannel) throws SocketException {
            this.socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
            this.socket.setSoTimeout(1000);
            this.minecraftChannel = minecraftChannel;
            this.receiveThread = new Thread(this::receiveLoop, "e4all-vc-client-proxy");
            this.receiveThread.setDaemon(true);
            this.receiveThread.start();
            LOGGER.debug("Started client UDP proxy on port {}", socket.getLocalPort());
        }
        public int getLocalPort() {
            return socket.getLocalPort();
        }
        public void onServerData(byte[] data) {
            SocketAddress addr = svcClientAddr;
            if (addr == null) return; 
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length, addr);
                socket.send(packet);
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.warn("Failed to forward voice data to SVC client", e);
                }
            }
        }
        private void receiveLoop() {
            byte[] buf = new byte[4096];
            while (running.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    if (!running.get() || !minecraftChannel.isActive()) break;
                    svcClientAddr = packet.getSocketAddress();
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    VoiceChatPacketHelper.sendVoiceData(minecraftChannel, data, false);
                } catch (SocketTimeoutException ignored) {
                } catch (IOException e) {
                    if (running.get()) {
                        LOGGER.debug("UDP receive error in client proxy", e);
                    }
                }
            }
        }
        public void close() {
            running.set(false);
            socket.close();
            LOGGER.debug("Closed client UDP proxy");
        }
    }
}