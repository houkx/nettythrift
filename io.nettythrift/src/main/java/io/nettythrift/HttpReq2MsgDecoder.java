package io.nettythrift;

import java.net.URLDecoder;
import java.util.List;

import org.apache.thrift.protocol.TProtocolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.nettythrift.transport.ThriftTransportType;

public class HttpReq2MsgDecoder extends MessageToMessageDecoder<FullHttpRequest> {
	private static Logger logger = LoggerFactory.getLogger(HttpReq2MsgDecoder.class);
	private final String proxyInfo;
	private final ServerConfig serverDef;
	private final boolean fromProgram;

	public HttpReq2MsgDecoder(ServerConfig serverDef, String proxyInfo, boolean fromProgram) {
		this.proxyInfo = proxyInfo;
		this.serverDef = serverDef;
		this.fromProgram = fromProgram;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, FullHttpRequest msg, List<Object> out) throws Exception {
		String queryStr = msg.uri();
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
		logger.debug("decode queryStr ={}, method={}, msg={}", queryStr, msg.method(), msg);
		// System.out.printf("decode queryStr = %s ,method=%s, msgId=%d,
		// msg=%s\n", queryStr, msg.getMethod(),
		// System.identityHashCode(msg),
		// msg);
		ByteBuf content;
		if (queryStr.length() == 0) {
			content = msg.retain().content();
		} else {
			queryStr = URLDecoder.decode(queryStr, "UTF-8");
			int strLen = queryStr.length();
			if (queryStr.charAt(0) != '[' && strLen > 5) {
                boolean wordOrLetter = Character.isLetterOrDigit(queryStr.charAt(strLen - 1));
				for (int i = 2, MAX = Math.min(strLen, 7); i < MAX; i++) {
					char c = queryStr.charAt(strLen - i);
					if (c == '.') {
						if (wordOrLetter) {
							serverDef.getHttpResourceHandler().process(ctx, msg, queryStr,strLen - i);
							return;
						}
						break;
					}else if(wordOrLetter && !Character.isLetterOrDigit(c)){
						wordOrLetter = false;
					}
				}
			}
			byte[] bytes = queryStr.getBytes();
			// System.err.println("URI: bytes[0] = "+bytes[0]+", len =
			// "+bytes.length);
			content = Unpooled.wrappedBuffer(bytes).retain();
		}
		TProtocolFactory factory = serverDef.getProcessor().getProtocolFactory(content);
		logger.debug("TProtocolFactory = {}", factory);
		if (factory != null && content.isReadable()) {

			ThriftMessage thriftMessage = new ThriftMessage(content, ThriftTransportType.HTTP)
					.setProctocolFactory(factory);
			thriftMessage.fromProgram = fromProgram;
			thriftMessage.proxyInfo = proxyInfo;
			thriftMessage.connectionKeepAlive = HttpHeaderUtil.isKeepAlive(msg);
			out.add(thriftMessage);
		} else {
			ThriftMessage thriftMessage = new ThriftMessage(Unpooled.EMPTY_BUFFER, ThriftTransportType.HTTP);
			thriftMessage.responseCode = 404;
			thriftMessage.responseMessage = "res not found!";
			ctx.writeAndFlush(thriftMessage).addListener(ChannelFutureListener.CLOSE);
			logger.error("factory =={}, content={}", factory, content);
		}
	}

}
