package link.e4all;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@ChannelHandler.Sharable
public class VoiceChatBridgeInitializer extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4all-voicebridge");
    private final ChannelHandler originalHandler;
    private final boolean isServerSide;
    public VoiceChatBridgeInitializer(ChannelHandler originalHandler, boolean isServerSide) {
        this.originalHandler = originalHandler;
        this.isServerSide = isServerSide;
    }
    public ChannelHandler getOriginalHandler() {
        return originalHandler;
    }
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().addBefore(ctx.name(), null, originalHandler);
        if (ctx.channel().isRegistered()) {
            addBridgeHandler(ctx);
        }
    }
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        addBridgeHandler(ctx);
    }
    private void addBridgeHandler(ChannelHandlerContext ctx) {
        if (ctx.pipeline().context(this) == null) {
            return; 
        }
        Channel ch = ctx.channel();
        if (Config.INSTANCE.voiceChatBridgeEnabled.value()) {
            try {
                VoiceChatBridgeHandler bridgeHandler = new VoiceChatBridgeHandler(isServerSide);
                if (ch.pipeline().get("packet_handler") != null) {
                    ch.pipeline().addBefore("packet_handler", "e4all_voicebridge", bridgeHandler);
                    LOGGER.debug("Added voice chat bridge handler to {} pipeline", isServerSide ? "server" : "client");
                } else {
                    ch.pipeline().addLast("e4all_voicebridge", bridgeHandler);
                    LOGGER.debug("Added voice chat bridge handler to {} pipeline (at end, packet_handler not found)", isServerSide ? "server" : "client");
                }
                if (ch.pipeline().get("splitter") != null) {
                    ch.pipeline().addAfter("splitter", "e4all_vc_raw_codec", new VoiceChatRawCodec(bridgeHandler));
                    LOGGER.debug("Added voice chat raw codec after splitter");
                } else if (ch.pipeline().get("decoder") != null) {
                    ch.pipeline().addBefore("decoder", "e4all_vc_raw_codec", new VoiceChatRawCodec(bridgeHandler));
                    LOGGER.debug("Added voice chat raw codec before decoder (splitter not found)");
                }
            } catch (Exception e) {
                LOGGER.debug("Could not add voice chat bridge handler", e);
            }
        }
        ctx.pipeline().remove(this);
    }
}