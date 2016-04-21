/**
 * 
 */
package io.nettythrift;

import java.net.SocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;

/**
 * 通用Thrift服务
 * 
 * @author HouKangxi
 *
 */
public class ThriftCommonServer extends CommonServer implements Runnable {
	private static Logger logger = LoggerFactory.getLogger(ThriftCommonServer.class);

	private ChannelGroup allChannels;

	public ThriftCommonServer(ServerConfig serverDef) {
		this(serverDef, new DefaultChannelGroup(new DefaultEventLoop()));
	}

	public ThriftCommonServer(ServerConfig serverDef, ChannelGroup allChannels) {
		this.serverDef = serverDef;
		this.allChannels = allChannels;
		Runtime.getRuntime().addShutdownHook(new Thread(this));
	}

	public ChannelGroup getAllChannels() {
		return allChannels;
	}

	@Override
	public void run() {
		logger.info("server closing[channels:{}]...", allChannels.size());
		close();
	}

	@Override
	public void close() {
		allChannels.close().awaitUninterruptibly();
		super.close();
	}

	public void start() throws Exception {
		start(serverDef.getPort(), handler);
	}

	class MonitorHandler extends ChannelHandlerAdapter {

		@Override
		public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
			logger.debug("*** channelRegistered");
			allChannels.add(ctx.channel());
			super.channelRegistered(ctx);
		}

		@Override
		public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
			logger.debug("*** channelUnregistered");
			allChannels.remove(ctx.channel());
			super.channelUnregistered(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			logger.debug("*** channelActive");
			super.channelActive(ctx);
		}

		@Override
		public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
				ChannelPromise promise) throws Exception {
			logger.debug("*** connect");
			super.connect(ctx, remoteAddress, localAddress, promise);
		}

		@Override
		public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
			logger.debug("*** disconnect");
			allChannels.remove(ctx.channel());
			super.disconnect(ctx, promise);
		}

		@Override
		public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
			logger.debug("*** close()");
			allChannels.remove(ctx.channel());
			super.close(ctx, promise);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			logger.error("exceptionCaught", cause);
		}

	}

	private final ServerConfig serverDef;
	private final ChannelInitializer<SocketChannel> handler = new ChannelInitializer<SocketChannel>() {
		@Override
		protected void initChannel(SocketChannel ch) throws Exception {
			ChannelPipeline pl = ch.pipeline();
			int maxFrame = serverDef.getMaxFrameLength();

			pl.addLast(new MonitorHandler());

			pl.addLast("msgDecoder", new ThriftMessageDecoder(serverDef));

			pl.addLast("msgEncoder", new ThriftMessageEncoder(maxFrame));

			pl.addLast("serverHandler", new ThriftServerHandler(serverDef));

		}
	};

}
