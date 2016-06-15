/**
 * 
 */
package io.nettythrift;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

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
import io.netty.util.concurrent.ScheduledFuture;

/**
 * 通用Thrift服务
 * 
 * @author HouKangxi
 *
 */
public class ThriftCommonServer extends CommonServer implements Runnable {
	private static Logger logger = LoggerFactory.getLogger(ThriftCommonServer.class);

	private final ChannelGroup allChannels;
	private final ServerConfig serverDef;

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

	public void start(int bossThreads, int workThreads) throws Exception {
		start(serverDef.getPort(), handler, bossThreads, workThreads);
	}

	class MonitorHandler extends ChannelHandlerAdapter {
		ScheduledFuture<?> idleFuture;
		long lastActiveTime;

		@Override
		public void read(ChannelHandlerContext ctx) throws Exception {
			// logger.debug("*** read");
			lastActiveTime = System.currentTimeMillis();
			super.read(ctx);
		}

		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			// logger.debug("*** write");
			lastActiveTime = System.currentTimeMillis();
			super.write(ctx, msg, promise);
		}

		@Override
		public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
			// logger.debug("*** channelRegistered");
			allChannels.add(ctx.channel());
			idleFuture = ctx.executor().scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					long now = System.currentTimeMillis();
					if (now - lastActiveTime >= serverDef.getIdleTimeMills()) {
						logger.debug("*** CLOSE Idle connection :{} ***", ctx.channel());
						idleFuture.cancel(false);
						ctx.close();
					} /*
						 * else { System.err.println("now: " + now +
						 * ",now - lastActiveTime = " + (now - lastActiveTime) +
						 * ", IdleTimeMills=" + serverDef.getIdleTimeMills()); }
						 */
				}
			}, 1000, serverDef.getIdleTimeMills(), TimeUnit.MILLISECONDS);
			super.channelRegistered(ctx);
		}

		@Override
		public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
			// logger.debug("*** channelUnregistered");
			if (idleFuture != null) {
				idleFuture.cancel(false);
			}
			allChannels.remove(ctx.channel());
			super.channelUnregistered(ctx);
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// logger.debug("*** channelActive");
			super.channelActive(ctx);
		}

		@Override
		public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
				ChannelPromise promise) throws Exception {
			// logger.debug("*** connect");
			super.connect(ctx, remoteAddress, localAddress, promise);
		}

		@Override
		public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
			// logger.debug("*** disconnect");
			allChannels.remove(ctx.channel());
			super.disconnect(ctx, promise);
		}

		@Override
		public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
			// logger.debug("*** close()");
			idleFuture.cancel(false);
			allChannels.remove(ctx.channel());
			super.close(ctx, promise);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			if (cause instanceof java.io.IOException) {
				logger.error("exceptionCaught", cause);
				return;
			}
			super.exceptionCaught(ctx, cause);
		}

	}

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
