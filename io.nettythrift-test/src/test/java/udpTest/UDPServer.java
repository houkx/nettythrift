package udpTest;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * @filename UDPServer.java
 * @author code by jianghuiwen
 * @mail jianghuiwen2012@163.com
 *
 *       下午4:14:17
 */
public class UDPServer {

	/**
	 * @param args
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		Bootstrap b = new Bootstrap();
		EventLoopGroup group = new NioEventLoopGroup();
		b.group(group).channel(NioDatagramChannel.class)//
				.option(ChannelOption.SO_BROADCAST, true)//
				.handler(new UDPSeverHandler());

		b.bind(9999).sync().channel().closeFuture().await();
	}

}