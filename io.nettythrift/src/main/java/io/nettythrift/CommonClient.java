package io.nettythrift;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

public class CommonClient {

	public void start(String host, int port, ChannelHandler channelHandler)
			throws Exception {
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			Bootstrap b = new Bootstrap(); // #1
			b.group(group) // #2
					.channel(NioSocketChannel.class) // #3
					.remoteAddress(new InetSocketAddress(host, port)) // #4
					.handler(channelHandler);
			ChannelFuture f = b.connect().sync(); // #7
			doOnChannelFuture(f);
			f.channel().closeFuture().sync(); // #8
		} finally {
			group.shutdownGracefully().sync(); // #9
		}
	}
	
	protected void doOnChannelFuture(ChannelFuture f){
	}
}
