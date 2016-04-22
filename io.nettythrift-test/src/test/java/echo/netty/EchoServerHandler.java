package echo.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@Sharable
// #1
public class EchoServerHandler extends SimpleChannelInboundHandler<Object> {
	@Override
	public void messageReceived(ChannelHandlerContext ctx, Object msg) {
		System.out.println("Server received: @" + System.identityHashCode(msg)
				+ " " + msg);
		ctx.write(msg);// #2
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("#channelActive");
		super.channelActive(ctx);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		System.out.println("#channelReadComplete");
		ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
				ChannelFutureListener.CLOSE); // #3
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace(); // #4
		ctx.close(); // #5
	}
}