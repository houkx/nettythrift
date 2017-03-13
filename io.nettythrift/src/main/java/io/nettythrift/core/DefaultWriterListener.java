package io.nettythrift.core;

import org.apache.thrift.TBase;
import org.apache.thrift.protocol.TMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public class DefaultWriterListener implements WriterHandler {
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

	@SuppressWarnings({ "rawtypes" })
	@Override
	public void beforeWrite(TMessage msg, TBase args, TBase result) {
		// reuse message's buffer when write? yes, we use the pool.
		ByteBuf readedBuf = message.getContent();
		int refCount = readedBuf.refCnt();
		if (refCount > 0) {
			readedBuf.release(refCount);
		}
		// voidMethod's return message is very short
		int initialCapacity = serverDef.trafficForecast.getInitBytesForWrite(msg.name);
		// logger.debug("initialCapacity = {} , msg = {}",initialCapacity, msg);
		ByteBuf buf = ctx.alloc().buffer(initialCapacity, serverDef.maxFrameSize);
		message.setContent(buf).beforeWrite(ctx);
		transport.setOutputBuffer(buf);
	}

	@SuppressWarnings({ "rawtypes" })
	@Override
	public void afterWrite(TMessage msg, Throwable cause, int code, TBase args, TBase result) {
		if (transport.isHasFlush()) {
			message.write(ctx);
			serverDef.trafficForecast.saveWritedBytes(msg.name, transport.getWrittenByteCount(), args, result);
		} else {
			message.getContent().release();
			logger.error("fail to process! code={}", code, cause);
		}
	}
}