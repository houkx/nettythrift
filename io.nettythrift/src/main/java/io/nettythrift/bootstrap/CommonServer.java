package io.nettythrift.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class CommonServer implements java.io.Closeable {
	private static Logger logger = LoggerFactory.getLogger(CommonServer.class);
	private NioEventLoopGroup bossGroup;
	private NioEventLoopGroup workerGroup;
	private ChannelFuture f;

	public void start(int port, ChannelHandler channelInitializer) throws Exception {
		start(port, channelInitializer, 0, 0);
	}

	public void start(int port, ChannelHandler channelInitializer, int bossThreads, int workThreads)
			throws Exception {
		bossGroup = new NioEventLoopGroup(bossThreads);
		workerGroup = new NioEventLoopGroup(workThreads);
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.option(ChannelOption.SO_BACKLOG, Integer.parseInt(System.getProperty("so.BACKLOG", "100")))
					// .option(ChannelOption.SO_KEEPALIVE,
					// Boolean.parseBoolean(System.getProperty("so.KEEPALIVE",
					// "true")))
					// .option(ChannelOption.SO_LINGER,
					// Integer.parseInt(System.getProperty("so.LINGER", "0")))
					.option(ChannelOption.SO_REUSEADDR,
							Boolean.parseBoolean(System.getProperty("so.REUSEADDR", "true")))
					 .handler(new LoggingHandler(LogLevel.DEBUG))
					.childHandler(channelInitializer);
			f = b.bind(port).sync();
			logger.info("Server started and listen on port:{}", port);
			f.channel().closeFuture().sync();
		} finally {
			close_();
		}
	}

	private void close_() {
		logger.info("**** try shutdown NioEventLoopGroups.");
		try {
			bossGroup.shutdownGracefully().sync();
		} catch (InterruptedException e) {
		}
		try {
			workerGroup.shutdownGracefully().sync();
		} catch (InterruptedException e) {
		}
	}

	public void stop() {
		close();
	}

	public void close() {
		logger.info("try shutdown NioEventLoopGroups.");
		try {
			bossGroup.shutdownGracefully().sync();
		} catch (InterruptedException e) {
		}
		f.channel().close();
	}
}
