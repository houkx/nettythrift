/**
 * 
 */
package io.nettythrift;

import java.util.List;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.nettythrift.transport.TNiftyTransport;
import io.nettythrift.transport.ThriftTransportType;

/**
 * @author HouKangxi
 *
 */
public class ThriftMessageDecoder extends ByteToMessageDecoder {
	private static Logger logger = LoggerFactory.getLogger(ThriftMessageDecoder.class);
	public static final int MESSAGE_FRAME_SIZE = 4;
	private final int maxFrameSize;
	private final ProxyHandler proxyHandler;
	private final ServerConfig serverDef;

	public ThriftMessageDecoder(ServerConfig serverDef) {
		this.maxFrameSize = serverDef.getMaxFrameLength();
		this.proxyHandler = serverDef.getProxyHandler();
		this.serverDef = serverDef;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		Object msg = decode(ctx, in);
		if (msg != null) {
			out.add(msg);
		}
	}
	private String proxyInfo;
	private boolean firstInvokeProxyHandler = true;
	
	private Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
		// return super.decode(ctx, in);
		if (!buffer.isReadable()) {
			return null;
		}
		if (firstInvokeProxyHandler && proxyHandler != null) {
			firstInvokeProxyHandler = false;
			proxyInfo = proxyHandler.getHeadProxyInfo(buffer);
			if (!buffer.isReadable()){
				return null;
			}
		}
		short firstByte = buffer.getUnsignedByte(0);
		logger.debug("decode():firstByte = {}", firstByte);
		// System.out.printf("decode():firstByte = %s,", firstByte);
		if (firstByte == 80) {
			logger.debug("httpRequest from program.");
			notifyHttpDecoder(ctx, buffer, proxyInfo);
			return null;
		}
		if (firstByte == 71) {// 从浏览器访问时首字符是71
			logger.debug("HttpRequest from brower.");
			notifyHttpDecoder(ctx, buffer, proxyInfo);
			return null;
		}

		TProtocolFactory fac = null;
		if ((fac = serverDef.getProcessor().getProtocolFactory(buffer)) != null) {

			logger.debug("TTProtocolFactory = {} when UnframedMessage ", fac);
			ByteBuf messageBuffer = tryDecodeUnframedMessage(ctx, buffer, fac);

			if (messageBuffer == null) {
				return null;
			}
			// A non-zero MSB for the first byte of the message implies the
			// message starts with a
			// protocol id (and thus it is unframed).
			return new ThriftMessage(messageBuffer, ThriftTransportType.UNFRAMED).setProctocolFactory(fac)
					.setProxyInfo(proxyInfo);

		} else if (buffer.readableBytes() < MESSAGE_FRAME_SIZE) {
			// Expecting a framed message, but not enough bytes available to
			// read the frame size
			return null;
		} else {
			ByteBuf messageBuffer = tryDecodeFramedMessage(ctx, buffer, true);

			if (messageBuffer == null) {
				return null;
			}
			TProtocolFactory factory = serverDef.getProcessor().getProtocolFactory(messageBuffer);
			logger.debug("TTProtocolFactory = {} when FramedMessage ", factory);
			// Messages with a zero MSB in the first byte are framed messages
			return new ThriftMessage(messageBuffer, ThriftTransportType.FRAMED).setProctocolFactory(factory)
					.setProxyInfo(proxyInfo);
		}
	}

	private void notifyHttpDecoder(ChannelHandlerContext ch, ByteBuf buffer, String proxyInfo) {
		ch.pipeline().addAfter("msgDecoder", "httpReqDecoder", new HttpRequestDecoder());
		ch.pipeline().addAfter("httpReqDecoder", "httpRespEncoder", new HttpResponseEncoder());
		ch.pipeline().addAfter("httpRespEncoder", "httpAggegator", new HttpObjectAggregator(512 * 1024, true));
		ch.pipeline().addAfter("httpAggegator", "httpReq2ThriftMsgDecoder", new HttpReq2MsgDecoder(serverDef,proxyInfo));
		ch.fireChannelRead(buffer);// 往下个ChannelInBoundHandler 传递
	}

	private ByteBuf tryDecodeFramedMessage(ChannelHandlerContext ctx, ByteBuf buffer, boolean stripFraming) {
		System.out.println("tryDecodeFramedMessage...");
		// Framed messages are prefixed by the size of the frame (which doesn't
		// include the
		// framing itself).

		int messageStartReaderIndex = buffer.readerIndex();
		int messageContentsOffset;

		if (stripFraming) {
			messageContentsOffset = messageStartReaderIndex + MESSAGE_FRAME_SIZE;
		} else {
			messageContentsOffset = messageStartReaderIndex;
		}

		// The full message is larger by the size of the frame size prefix
		int messageLength = buffer.getInt(messageStartReaderIndex) + MESSAGE_FRAME_SIZE;
		int messageContentsLength = messageStartReaderIndex + messageLength - messageContentsOffset;
		System.out.printf("messageLength = %d, messageContentsLength=%d, offset=%d\n",messageLength,messageContentsLength,messageContentsOffset);
		if (messageContentsLength > maxFrameSize) {
			throw new TooLongFrameException("Maximum frame size of " + maxFrameSize + " exceeded");
		}

		if (messageLength == 0) {
			// Zero-sized frame: just ignore it and return nothing
			buffer.readerIndex(messageContentsOffset);
			return null;
		} else if (buffer.readableBytes() < messageLength) {
			// Full message isn't available yet, return nothing for now
			return null;
		} else {
			// Full message is available, return it
			ByteBuf messageBuffer = extractFrame(ctx, buffer, messageContentsOffset, messageContentsLength);
			buffer.readerIndex(messageStartReaderIndex + messageLength);
			return messageBuffer;
		}
	}

	private ByteBuf tryDecodeUnframedMessage(ChannelHandlerContext ctx, ByteBuf buffer,
			TProtocolFactory inputTProtocolFactory) throws TException {
		// Perform a trial decode, skipping through
		// the fields, to see whether we have an entire message available.

		int messageLength = 0;
		int messageStartReaderIndex = buffer.readerIndex();
		Channel channel = ctx.channel();
		try {
			TNiftyTransport decodeAttemptTransport = new TNiftyTransport(channel, buffer, TNiftyTransport.MOD_R);
			int initialReadBytes = decodeAttemptTransport.getReadByteCount();
			
			TProtocol inputProtocol = inputTProtocolFactory.getProtocol(decodeAttemptTransport);

			// Skip through the message
			inputProtocol.readMessageBegin();
			TProtocolUtil.skip(inputProtocol, TType.STRUCT);
			inputProtocol.readMessageEnd();

			messageLength = decodeAttemptTransport.getReadByteCount() - initialReadBytes;
		} catch (TTransportException e) {
			// No complete message was decoded: ran out of bytes
			logger.error("fail to tryDecodeUnframedMessage", e);
			return null;
		} catch (IndexOutOfBoundsException e) {
			// No complete message was decoded: ran out of bytes
			logger.error("fail to tryDecodeUnframedMessage", e);
			return null;
		} finally {
			if (buffer.readerIndex() - messageStartReaderIndex > maxFrameSize) {
				throw new TooLongFrameException("Maximum frame size of " + maxFrameSize + " exceeded");
			}

			buffer.readerIndex(messageStartReaderIndex);
		}

		if (messageLength <= 0) {
			return null;
		}

		// We have a full message in the read buffer, slice it off
		ByteBuf messageBuffer = extractFrame(ctx, buffer, messageStartReaderIndex, messageLength);
		buffer.readerIndex(messageStartReaderIndex + messageLength);
		return messageBuffer;
	}

	protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
		return buffer.slice(index, length).retain();
	}
}
