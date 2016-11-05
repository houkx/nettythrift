package io.nettythrift.core;

import org.apache.thrift.protocol.TMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public class DefaultWriterListener implements WriteListener {
	private static final Logger logger = LoggerFactory.getLogger(DefaultWriterListener.class);
	private final ThriftMessage message;
	private final TNettyTransport transport;
	private final ChannelHandlerContext ctx;
	private final ThriftServerDef serverDef;

	public DefaultWriterListener(ThriftMessage message, TNettyTransport transport, ChannelHandlerContext ctx,
			ThriftServerDef serverDef) {
		this.message = message;
		this.transport = transport;
		this.ctx = ctx;
		this.serverDef = serverDef;
	}

	@Override
	public void beforeWrite(TMessage msg) {
		// TODO reuse message's buffer?
		// voidMethod's return message is very short
		ByteBuf buf = ctx.alloc().buffer(serverDef.isVoidMethod(msg.name) ? 128 : 1024, serverDef.maxFrameSize)
				.retain();
		logger.debug("beforeWrite: buf's cap = {}", buf.capacity());
		message.setContent(buf).beforeWrite(ctx);
		transport.setOutputBuffer(buf);
	}

	@Override
	public void afterWrite(TMessage msg, Throwable cause, int code) {
		if (transport.isHasFlush()) {
			message.setContent(transport.getOutputBuffer()).write(ctx);
		} else {
			logger.error("fail to process! code={}", code, cause);
		}
	}
}