/**
 * 
 */
package io.nettythrift.codec;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 *
 * PROXY protocol Decoder
 * 
 * @see {@link http://docs.aws.amazon.com/elasticloadbalancing/latest/classic/enable-proxy-protocol.html#proxy-protocol
 *      }
 * 
 * @author HouKx
 */
public class AwsProxyProtocolDecoder extends ByteToMessageDecoder {
	private static Logger logger = LoggerFactory.getLogger(AwsProxyProtocolDecoder.class);
	public static final AttributeKey<String> KEY_PROXY = AttributeKey.valueOf("TCP_PROXY");
	private static final int start = 11;

	public AwsProxyProtocolDecoder() {
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
		if (!isProxyProtocol(buffer)) {
			ctx.fireChannelRead(buffer.retain());// 往下个ChannelInBoundHandler 传递
			return;
		}
		Attribute<String> attr = ctx.attr(KEY_PROXY);
		if (attr.get() != null) {
			logger.debug("ProxyProtocolDecoder: 已解析过代理");
			ctx.fireChannelRead(buffer.retain());// 往下个ChannelInBoundHandler 传递
			return;
		}
		logger.debug("尝试解析代理");
		int len = Math.min(buffer.readableBytes(), 10);
		if (len < 10) {
			ctx.fireChannelRead(buffer.retain());// 往下个ChannelInBoundHandler 传递
			return;
		}
		if (buffer.getByte(9) == '4') {// PROXY TCP4
			// System.out.println("IPV4");
			int ipEnd = -1;

			for (int i = start + 3 * 4 + 3; i > 0; i--) {
				if (buffer.getByte(i) == ' ') {
					ipEnd = i;
					break;
				}
			}
			if (ipEnd > start) {
				char ip[] = new char[ipEnd - start];
				for (int i = 0; i < ip.length; i++) {
					ip[i] = (char) buffer.getByte(start + i);
				}
				// 修改readerIndex 到字符串末尾的\r\n
				int i = ipEnd + 1 + (1 * 4 + 3 + 2 * 2 + 2);
				ipEnd = -1;
				for (int MAX = buffer.readerIndex() + buffer.readableBytes(); i < MAX; i++) {
					if (buffer.getByte(i) == '\n') {
						ipEnd = i;
						break;
					}
				}
				int newIndex = ipEnd + 1;
				if (ipEnd > 0) {
					buffer.readerIndex(newIndex);
				}
				String clientIp = new String(ip);
				logger.debug("代理IP: {}, buffer.readerIndex={}", clientIp, buffer.readerIndex());
				attr.set(clientIp);
			}
		} else {
			System.err.println("IPV" + buffer.getByte(9) + "?");
		}
		logger.debug("proxy最后接力");
		ctx.fireChannelRead(buffer.retain());// 往下个ChannelInBoundHandler
		// 传递
	}

	private boolean isProxyProtocol(ByteBuf buffer) {
		int len = Math.min(buffer.readableBytes(), 10);
		if (len < 6) {
			return false;
		}
		byte[] dst = new byte[len];
		buffer.getBytes(buffer.readerIndex(), dst, 0, len);
		if (logger.isDebugEnabled()) {
			logger.debug("headCode: head={}, bytes={}", new String(dst), Arrays.toString(dst));
		}
		switch (dst[0]) {
		case 'P': {
			switch (dst[1]) {
			case 'R': {
				// PROXY protocol , see:
				// http://docs.aws.amazon.com/elasticloadbalancing/latest/classic/enable-proxy-protocol.html#proxy-protocol
				if (dst[2] == 'O' && dst[3] == 'X' && dst[4] == 'Y' && dst[5] == ' '
						&& (dst.length > 8 && (dst[6] + dst[7] + dst[8] == 'T' + 'C' + 'P'))) {
					return true;
				}
			}
			}
			break;
		}
		}
		return false;
	}
}
