package echo.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@Sharable
public class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		//
		// ctx.writeAndFlush(Unpooled.copiedBuffer("Netty rocks!",
		// CharsetUtil.UTF_8)); // #2
		ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 125 })); // #2
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, ByteBuf in) {
		System.out.println("Client received: "
				+ ByteBufUtil.hexDump(in.readBytes(in.readableBytes()))); // #4
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, // #5
			Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}
}