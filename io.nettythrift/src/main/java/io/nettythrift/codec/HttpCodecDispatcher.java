/**
 * 
 */
package io.nettythrift.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.nettythrift.core.ThriftServerDef;
import io.nettythrift.utils.HttpMethodUtil;

/**
 * 判断当前请求是不是http请求，如果是的话则添加http相关的编解码器
 * 
 * @author HouKx
 *
 */
public class HttpCodecDispatcher extends ChannelHandlerAdapter {
	private final ThriftServerDef serverDef;

	public HttpCodecDispatcher(ThriftServerDef serverDef) {
		super();
		this.serverDef = serverDef;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof ByteBuf && ctx.channel().isActive()) {
			boolean isHttpRequest = false;
			ByteBuf buffer = (ByteBuf) msg;
			final int len = 11;
			if (buffer.readableBytes() > len) {
				byte[] dst = new byte[len];
				buffer.getBytes(buffer.readerIndex(), dst, 0, len);
				int n = HttpMethodUtil.method(dst);
				isHttpRequest = n > 2;
			}
			if (isHttpRequest) {
				ChannelPipeline cp = ctx.pipeline();
				String currentName = ctx.name();
				cp.addAfter(currentName, "HttpRequestDecoder", new HttpRequestDecoder());
				cp.addAfter("HttpRequestDecoder", "HttpResponseEncoder", new HttpResponseEncoder());
				cp.addAfter("HttpResponseEncoder", "HttpObjectAggregator", new HttpObjectAggregator(512 * 1024));
				ChannelHandler handler = serverDef.httpHandlerFactory.create(serverDef);
				cp.addAfter("HttpObjectAggregator", "HttpThriftBufDecoder", handler);

				cp.remove(currentName);
			}
		}
		ctx.fireChannelRead(msg);
	}

}
