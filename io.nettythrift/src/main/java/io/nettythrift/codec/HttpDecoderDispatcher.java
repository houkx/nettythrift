/**
 * 
 */
package io.nettythrift.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.nettythrift.core.ThriftServerDef;

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
			cp.addAfter("HttpObjectAggregator", "HttpThriftBufDecoder", serverDef.httpHandlerFactory.create(serverDef));

			cp.remove(currentName);
		}
		ctx.fireChannelRead(buffer.retain());
	}

	protected boolean isHttpRequest(ByteBuf buffer) {
		int len = Math.min(buffer.readableBytes(), 10);
		if (len < 6) {
			return false;
		}
		byte[] dst = new byte[len];
		buffer.getBytes(buffer.readerIndex(), dst, 0, len);
		switch (dst[0]) {
		case 'P': {
			switch (dst[1]) {
			case 'O': {
				// http POST 方法
				if (dst[2] == 'S' && dst[3] == 'T' && dst[4] == ' ' && dst[5] == '/') {
					return true;
				}
				break;
			}
			case 'U': {
				// http PUT 方法
				if (dst[2] == 'T' && dst[3] == ' ' && dst[4] == '/') {
					return true;
				}
				break;
			}
			default: {
				return false;
			}
			}
			break;
		}
		case 'G': {
			// http GET 方法
			if (dst[1] == 'E' && dst[2] == 'T' && dst[3] == ' ' && dst[4] == '/') {
				return true;
			}
			break;
		}
		// TODO http DELETE...
		}
		return false;
	}

}
