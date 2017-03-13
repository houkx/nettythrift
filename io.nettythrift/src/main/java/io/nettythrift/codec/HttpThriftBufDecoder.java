/**
 *
 */
package io.nettythrift.codec;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.nettythrift.core.ThriftMessage;
import io.nettythrift.core.ThriftMessageWrapper;
import io.nettythrift.core.ThriftServerDef;

/**
 * 主要功能:
 * <ul>
 * <li>负责提取http请求中的thrift协议部分(POST请求则认为body内容全部是thrift内容,GET请求则尝试把'?'之后的内容解析为thrift内容)</li>
 * <li>兼顾部分静态文件请求，后缀含'.'的（如/host/1.html）认为是静态文件请求</li>
 * <li>处理websocket请求，认为发送的普通文本是thrift请求，并标记上下文属性 标明当前是websocket Channel</li>
 * <li>只要是thrift协议，都会发送一个UserEvent,一个ThrfitMessageWraper对象</li>
 * </ul>
 * 
 * @author HouKx
 */
public class HttpThriftBufDecoder extends MessageToMessageDecoder<FullHttpRequest> {
	private static Logger logger = LoggerFactory.getLogger(HttpThriftBufDecoder.class);
	/**
	 * tag current channel is in WebSocket connection
	 */
	public static final AttributeKey<Boolean> KEY_WebsocketChannel = AttributeKey.valueOf("#WsChannel");

	private final ThriftServerDef serverDef;

	public HttpThriftBufDecoder(ThriftServerDef serverDef) {
		super();
		this.serverDef = serverDef;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, FullHttpRequest request, List<Object> out) throws Exception {
		// Handle a bad request.
		if (!request.decoderResult().isSuccess()) {
			sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
			return;
		}
		if ("websocket".equalsIgnoreCase((String) request.headers().get("Upgrade"))) {
			handleWebSocket(ctx, request);
			return;
		}

		String queryStr = request.uri();
		if ("/favicon.ico".equals(queryStr)) {
			FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
			sendHttpResponse(ctx, request, res);
			return;
		}
		if (queryStr != null) {
			queryStr = queryStr.trim();
			if (queryStr.length() > 0 && queryStr.charAt(0) == '/') {
				if (queryStr.length() == 1) {
					queryStr = "";
				} else {
					int wh = queryStr.indexOf('?');
					if (wh > 0) {
						queryStr = queryStr.substring(wh + 1);
					} else {
						queryStr = queryStr.substring(1);
					}
				}
			}
		} else {
			queryStr = "";
		}
		HttpMethod method = request.method();
		logger.debug("decode queryStr ={}, method={}, msg={}", queryStr, method, request);
		if (directHandleMethod(ctx, request, method)) {
			return;
		}
		ByteBuf content;
		if (queryStr.length() == 0) {
			if (HttpMethod.GET.equals(method)) {
				handleHttpHomePage(ctx, request);
				return;
			}
			content = request.content().retain();
		} else {
			queryStr = URLDecoder.decode(queryStr, "UTF-8");
			int strLen = queryStr.length();
			if (queryStr.charAt(0) != '[' && strLen > 5) {
				boolean wordOrLetter = Character.isLetterOrDigit(queryStr.charAt(strLen - 1));
				for (int i = 2, MAX = Math.min(strLen, 7); i < MAX; i++) {
					char c = queryStr.charAt(strLen - i);
					if (c == '.') {
						if (wordOrLetter) {
							serverDef.httpResourceHandler.process(ctx, request, queryStr, strLen - i);
							return;
						}
						break;
					} else if (wordOrLetter && !Character.isLetterOrDigit(c)) {
						wordOrLetter = false;
					}
				}
			}
			byte[] bytes = queryStr.getBytes();
			// System.err.println("URI: bytes[0] = "+bytes[0]+", len =
			// "+bytes.length);
			content = ctx.alloc().buffer(bytes.length);
			content.writeBytes(bytes);
		}
		logger.debug("content.size = " + content.readableBytes());
		out.add(content);
		boolean alive = HttpHeaderUtil.isKeepAlive(request);
		Object event = alive ? thriftMessageWrapperKeepAlive : thriftMessageWrapperNormal;
		ctx.fireUserEventTriggered(event);
	}

	private boolean directHandleMethod(ChannelHandlerContext ctx, FullHttpRequest request, HttpMethod method) {
		if (method.equals(HttpMethod.GET) || method.equals(HttpMethod.POST)) {
			return false;
		}
		// 处理 OPTIONS 请求
		HttpResponseStatus status = HttpResponseStatus.OK;
		boolean invalid = false;
		if (!method.equals(HttpMethod.OPTIONS)) {
			invalid = true;
			status = HttpResponseStatus.METHOD_NOT_ALLOWED;
		}
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
		HttpHeaders headers = response.headers();
		// headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
		// "X-Requested-With, accept, origin, content-type");
		headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "X-Requested-With, content-type");
		headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,OPTIONS");
		headers.set(HttpHeaderNames.SERVER, "Netty5");
		if (invalid) {
			headers.set("Client-Warning", "Invalid Method");
		}
		boolean keepAlive = HttpHeaderUtil.isKeepAlive(request);
		if (keepAlive) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}
		ctx.write(response);
		ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		if (!keepAlive) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
		return true;
	}

	protected static final ThriftMessageWrapper thriftMessageWrapperNormal = new BaseHttpThriftMessageWrapperImpl();
	protected static final ThriftMessageWrapper thriftMessageWrapperKeepAlive = new BaseHttpThriftMessageWrapperImpl() {
		protected void filterResponse(DefaultFullHttpResponse httpResp, ThriftMessage msg) {
			httpResp.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(msg.getContent().readableBytes()));
			httpResp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		protected void afterFlush(ChannelFuture future) {
		}
	};

	protected static class BaseHttpThriftMessageWrapperImpl extends ThriftMessageWrapper {
		@Override
		public Object wrapMessage(ChannelHandlerContext ctx, ThriftMessage msg) {
			HttpResponseStatus status = HttpResponseStatus.OK;
			DefaultFullHttpResponse httpResp = new DefaultFullHttpResponse(HTTP_1_1, status, msg.getContent());
			HttpHeaders headers = httpResp.headers();
			headers.set(HttpHeaderNames.CONTENT_TYPE, "application/json");
			headers.set(HttpHeaderNames.SERVER, "Netty5");
			filterResponse(httpResp, msg);
			return httpResp;
		}

		protected void writeMessageInner(ChannelHandlerContext ctx, Object httpResp) {
			ctx.write(httpResp);
			ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
			afterFlush(future);
		}

		protected void filterResponse(DefaultFullHttpResponse httpResp, ThriftMessage msg) {
		}

		protected void afterFlush(ChannelFuture future) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	protected void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
		// Generate an error page if response getStatus code is not OK (200).
		if (res.status().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			HttpHeaderUtil.setContentLength(res, res.content().readableBytes());
		}

		// Send the response and close the connection if necessary.
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!HttpHeaderUtil.isKeepAlive(req) || res.status().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	/**
	 * handle the home page, such as: return index.html
	 *
	 * @param ctx
	 */
	protected void handleHttpHomePage(ChannelHandlerContext ctx, FullHttpRequest request) {
		File indexHtml = new File("index.html");
		if (indexHtml.exists()) {
			RandomAccessFile file = null;
			long length = 0;
			try {
				file = new RandomAccessFile(indexHtml, "r");
			} catch (FileNotFoundException e) {
			}
			if (file != null) {
				try {
					length = file.length();
				} catch (IOException ex) {
					length = indexHtml.length();
				}
				HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
				response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
				boolean keepAlive = HttpHeaderUtil.isKeepAlive(request);
				if (keepAlive) {
					response.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(length));
					response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
				}
				ctx.write(response);
				ctx.write(new DefaultFileRegion(file.getChannel(), 0, length));
				ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
				if (!keepAlive) {
					future.addListener(ChannelFutureListener.CLOSE);
				}
			}
			return;
		}
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
		boolean keepAlive = HttpHeaderUtil.isKeepAlive(request);
		ByteBuf homeBuffers = homeBuffers();
		if (keepAlive) {
			response.headers().set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(homeBuffers.readableBytes()));
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}
		ctx.write(response);
		ctx.write(homeBuffers);
		ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		if (!keepAlive) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	protected ByteBuf homeBuffers() {
		return Unpooled.wrappedBuffer(
				"<html><head><title>Netty5Thrift</title></head><body><h1>Welcome!</h1></body></html>".getBytes());
	}

	/**
	 * handle WebSocket request,then, the the RPC could happen in WebSocket.
	 * 
	 * @param ctx
	 * @param request
	 */
	protected void handleWebSocket(final ChannelHandlerContext ctx, FullHttpRequest request) {
		if (logger.isDebugEnabled()) {
			logger.debug("handleWebSocket request: uri={}", request.uri());
		}
		// Handshake
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(request.uri(), null, true);
		WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(request);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
			return;
		}
		ChannelFutureListener callback = websocketHandshakeListener(ctx, request);
		ChannelFuture future = handshaker.handshake(ctx.channel(), request);
		if (callback != null) {
			future.addListener(callback);
		}
		ChannelPipeline pipe = ctx.pipeline();
		if (pipe.get(WebsocketFrameHandler.class) == null) {
			pipe.addAfter(ctx.name(), "wsFrameHandler", new WebsocketFrameHandler(handshaker));
			ChannelHandler handlerAws = pipe.get(AwsProxyProtocolDecoder.class);
			if (handlerAws != null) {
				pipe.remove(handlerAws);
			}
			pipe.remove(ctx.name());// Remove current Handler
		}
	}

	protected ChannelFutureListener websocketHandshakeListener(final ChannelHandlerContext ctx,
			FullHttpRequest request) {
		return null;
	}

	protected static class TextWebSocketFrameThriftMessageWrapper extends ThriftMessageWrapper {
		@Override
		public Object wrapMessage(ChannelHandlerContext ctx, ThriftMessage msg) {
			// wrap the message to a TextWebSocketFrame
			return new TextWebSocketFrame(msg.getContent());
		}
	}

	protected static class WebsocketFrameHandler extends MessageToMessageDecoder<WebSocketFrame> {
		final WebSocketServerHandshaker handshaker;

		protected WebsocketFrameHandler(WebSocketServerHandshaker handshaker) {
			this.handshaker = handshaker;
		}

		@Override
		protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
			if (frame instanceof CloseWebSocketFrame) {
				handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
				return;
			}
			if (frame instanceof PingWebSocketFrame) {
				out.add(new PongWebSocketFrame(frame.content().retain()));
				return;
			}
			if (frame instanceof PongWebSocketFrame) {
				out.add(new PongWebSocketFrame(frame.content().retain()));
				return;
			}
			if (!(frame instanceof TextWebSocketFrame)) {
				throw new UnsupportedOperationException(
						String.format(" frame type '%s' not supported", frame.getClass().getName()));
			}
			// extract the message
			String message = ((TextWebSocketFrame) frame).text();
			logger.debug("ws '{}' received: {}", ctx.channel(), message);
			byte[] bytes = message.getBytes();
			ByteBuf content = Unpooled.wrappedBuffer(bytes);
			out.add(content.retain());
			ctx.fireUserEventTriggered(new TextWebSocketFrameThriftMessageWrapper());
			ctx.attr(KEY_WebsocketChannel).set(true);
		}

	}
}
