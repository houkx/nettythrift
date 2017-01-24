/**
 * 
 */
package io.nettythrift.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.nettythrift.core.ThriftServerDef;
import io.nettythrift.utils.HttpMethodUtil;

/**
 * @author HouKx
 *
 */
public class HttpDecoderDispatcher extends SimpleChannelInboundHandler<ByteBuf> {
	private final ThriftServerDef serverDef;

	public HttpDecoderDispatcher(ThriftServerDef serverDef) {
		super();
		this.serverDef = serverDef;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.netty.handler.codec.ByteToMessageDecoder#decode(io.netty.channel.
	 * ChannelHandlerContext, io.netty.buffer.ByteBuf, java.util.List)
	 */
	@Override
	protected void messageReceived(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
		if (isHttpRequest(buffer)) {
			ChannelPipeline cp = ctx.pipeline();
			String currentName = ctx.name();
			cp.addAfter(currentName, "HttpRequestDecoder", new HttpRequestDecoder());
			cp.addAfter("HttpRequestDecoder", "HttpResponseEncoder", new HttpResponseEncoder());
			cp.addAfter("HttpResponseEncoder", "HttpObjectAggregator", new HttpObjectAggregator(512 * 1024));
			ChannelHandler handler = serverDef.httpHandlerFactory.create(serverDef);
			cp.addAfter("HttpObjectAggregator", "HttpThriftBufDecoder", handler);

			cp.remove(currentName);
		}
		ctx.fireChannelRead(buffer.retain());
	}

	protected boolean isHttpRequest(ByteBuf buffer) {
		final int len = 11;
		if (buffer.readableBytes() < len) {
			return false;
		}
		byte[] dst = new byte[len];
		buffer.getBytes(buffer.readerIndex(), dst, 0, len);
		int n = HttpMethodUtil.method(dst);
		return n > 2;
	}

}
