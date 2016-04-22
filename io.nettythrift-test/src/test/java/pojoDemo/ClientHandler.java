package pojoDemo;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@Sharable
public class ClientHandler extends SimpleChannelInboundHandler<Response> {

	// @Override
	// public void channelActive(ChannelHandlerContext ctx) {
	// //
	// // ctx.writeAndFlush(Unpooled.copiedBuffer("Netty rocks!",
	// // CharsetUtil.UTF_8)); // #2
	// ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 125 }));
	// // #2
	// }

	@Override
	public void messageReceived(ChannelHandlerContext ctx, Response in) {
		System.out.println("Client received: " + in); // #4
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, // #5
			Throwable cause) throws Exception{
		super.exceptionCaught(ctx, cause);
		cause.printStackTrace();
		ctx.close();
	}
}