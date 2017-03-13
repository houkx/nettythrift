/**
 *
 */
package io.nettythrift.core;

import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.nettythrift.codec.AwsProxyProtocolDecoder;
import io.nettythrift.codec.HttpCodecDispatcher;
import io.nettythrift.codec.ThriftMessageDecoder;
import io.nettythrift.codec.ThriftMessageEncoder;

/**
 * @author HouKx
 */
public class DefaultChannelInitializer<CHANNEL extends Channel> extends ChannelInitializer<CHANNEL> {

	private final ThriftServerDef serverDef;

	public DefaultChannelInitializer(ThriftServerDef serverDef) {
		this.serverDef = serverDef;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * io.netty5thrift.core.ContextHandlerInstaller#installHandlers(io.netty.
	 * channel.ChannelPipeline)
	 */
	@Override
	protected void initChannel(CHANNEL channel) throws Exception {
		ChannelPipeline cp = channel.pipeline();
		cp.addLast("ProxyHandler", new AwsProxyProtocolDecoder());
		cp.addLast("HttpDispatcher", new HttpCodecDispatcher(serverDef));
		cp.addLast("ThriftMessageDecoder", new ThriftMessageDecoder(serverDef));
		cp.addLast("ThriftMessageEncoder", new ThriftMessageEncoder(serverDef));
		long idles = serverDef.clientIdleTimeout;
		if (idles > 0) {
			cp.addLast("IdleHandler", new IdleDisconnectHandler(idles, TimeUnit.MILLISECONDS));
		}
	}

}
