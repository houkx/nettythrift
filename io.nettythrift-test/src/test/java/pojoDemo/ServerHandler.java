package pojoDemo;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@Sharable
public class ServerHandler extends ChannelInboundHandlerAdapter {
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		System.out.println("Server received: @" + System.identityHashCode(msg) + " " + msg);
		Request req = (Request) msg;
		Response resp = new Response();
		resp.setId(req.getId());
		resp.setTime(System.currentTimeMillis());

		ctx.write(resp);// #2
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		// ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
		// ChannelFutureListener.CLOSE); // #3
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace(); // #4
		ctx.close(); // #5
	}
}