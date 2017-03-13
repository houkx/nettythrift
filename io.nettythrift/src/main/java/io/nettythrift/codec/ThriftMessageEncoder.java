/**
 *
 */
package io.nettythrift.codec;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.nettythrift.core.DefaultWriterListener;
import io.nettythrift.core.TNettyTransport;
import io.nettythrift.core.ThriftMessage;
import io.nettythrift.core.ThriftServerDef;

/**
 * @author HouKx
 */
public class ThriftMessageEncoder extends SimpleChannelInboundHandler<ThriftMessage> {
	private static Logger logger = LoggerFactory.getLogger(ThriftMessageEncoder.class);
	private final ThriftServerDef serverDef;

	public ThriftMessageEncoder(ThriftServerDef serverDef) {
		super(false);
		this.serverDef = serverDef;
	}

	@Override
	protected void messageReceived(ChannelHandlerContext ctx, ThriftMessage message) throws Exception {
		ByteBuf buffer = message.getContent();
		logger.debug("msg.content:: {}", buffer);
		try {
			TNettyTransport transport = new TNettyTransport(ctx.channel(), buffer);
			TProtocolFactory protocolFactory = message.getProtocolFactory();
			TProtocol protocol = protocolFactory.getProtocol(transport);
			serverDef.nettyProcessor.process(ctx, protocol, protocol,
					new DefaultWriterListener(message, transport, ctx, serverDef));
		} catch (Throwable ex) {
			int refCount = buffer.refCnt();
			if (refCount > 0) {
				buffer.release(refCount);
			}
			throw ex;
		}
	}

}
