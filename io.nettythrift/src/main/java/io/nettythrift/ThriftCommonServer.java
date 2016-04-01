/**
 * 
 */
package io.nettythrift;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * 通用Thrift服务
 * 
 * @author HouKangxi
 *
 */
public class ThriftCommonServer extends CommonServer {

	public ThriftCommonServer(ServerConfig serverDef) {
		this.serverDef = serverDef;
	}

	public void start() throws Exception {
		start(serverDef.getPort(), handler);
	}

	private final ServerConfig serverDef;
	private final ChannelInitializer<SocketChannel> handler = new ChannelInitializer<SocketChannel>() {
		@Override
		protected void initChannel(SocketChannel ch) throws Exception {
			ChannelPipeline pl = ch.pipeline();
			int maxFrame = serverDef.getMaxFrameLength();

			pl.addLast("msgDecoder", new ThriftMessageDecoder(serverDef));

			pl.addLast("msgEncoder", new ThriftMessageEncoder(maxFrame));

			pl.addLast("serverHandler", new ThriftServerHandler(serverDef));

		}
	};

}
