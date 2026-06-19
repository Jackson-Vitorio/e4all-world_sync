package link.e4all;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class VoiceChatRawCodec extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4all-voicebridge");
    private final VoiceChatBridgeHandler bridgeHandler;
    public VoiceChatRawCodec(VoiceChatBridgeHandler bridgeHandler) {
        this.bridgeHandler = bridgeHandler;
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf && buf.readableBytes() >= 4) {
            int magic = buf.getInt(buf.readerIndex());
            if (magic == VoiceChatPacketHelper.VOICE_FRAME_MAGIC) {
                buf.skipBytes(4);
                byte[] voiceData = new byte[buf.readableBytes()];
                buf.readBytes(voiceData);
                buf.release();
                bridgeHandler.handleRawVoiceData(voiceData);
                return;
            }
        }
        super.channelRead(ctx, msg);
    }
}