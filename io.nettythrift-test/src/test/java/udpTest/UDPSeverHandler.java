package udpTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

/**
 * @filename UDPSeverHandler.java
 * @author code by jianghuiwen
 * @mail jianghuiwen2012@163.com
 *
 *       下午4:21:10
 */
public class UDPSeverHandler extends SimpleChannelInboundHandler<DatagramPacket> {
	private static Logger logger = LoggerFactory.getLogger(UDPSeverHandler.class);
	@Override
	protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
		ByteBuf buf = (ByteBuf) packet.copy().content();
		byte[] req = new byte[buf.readableBytes()];
		buf.readBytes(req);
		String body = new String(req, "UTF-8");
		logger.debug("body: {}", body);

	}

	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		// TODO Auto-generated method stub
		super.channelRegistered(ctx);
	}

}