/**
 *
 */
package io.nettythrift.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.nettythrift.core.*;

import org.apache.thrift.protocol.*;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 负责把用户请求的thrift协议内容解析为一个ThriftMessage 对象
 * 
 * @author HouKx
 */
public class ThriftMessageDecoder extends ByteToMessageDecoder {
	private static Logger logger = LoggerFactory.getLogger(ThriftMessageDecoder.class);
	public static final int MESSAGE_FRAME_SIZE = 4;

	private final ThriftServerDef serverDef;
	private final int maxFrameSize;

	public ThriftMessageDecoder(ThriftServerDef serverDef) {
		this.serverDef = serverDef;
		maxFrameSize = serverDef.maxFrameSize;
	}

	private ThriftMessageWrapper successor;

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof ThriftMessageWrapper) {
			successor = (ThriftMessageWrapper) evt;
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see io.netty.handler.codec.ByteToMessageDecoder#decode(io.netty.channel.
	 * ChannelHandlerContext, io.netty.buffer.ByteBuf, java.util.List)
	 */
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		if (ctx.channel().isActive()) {
			ThriftMessage msg = decodeMessage(ctx, in);
			logger.debug("decodeMessage() return:{}", msg);
			if (msg != null) {
				msg.getContent().retain();
				out.add(msg);
			}
		}
	}

	protected ThriftMessage decodeMessage(ChannelHandlerContext ctx, ByteBuf buffer)
			throws Exception {

		TProtocolFactory inputProtocolFactory = getProtocolFactory(buffer);

		if (inputProtocolFactory != null) {
			ByteBuf messageBuffer = tryDecodeUnframedMessage(ctx, ctx.channel(), buffer, inputProtocolFactory);

			if (messageBuffer == null) {
				return null;
			}

			// A non-zero MSB for the first byte of the message implies the
			// message starts with a
			// protocol id (and thus it is unframed).
			return new ThriftMessage(messageBuffer, inputProtocolFactory).setWrapper(unframedMessageWrapper(successor));
		} else if (buffer.readableBytes() < MESSAGE_FRAME_SIZE) {
			// Expecting a framed message, but not enough bytes available to
			// read the frame size
			return null;
		} else {
			ByteBuf messageBuffer = tryDecodeFramedMessage(ctx, ctx.channel(), buffer);

			if (messageBuffer == null) {
				return null;
			}
			inputProtocolFactory = getProtocolFactory(messageBuffer);
			logger.debug("get inputProtocolFactory [{}] for frameRequest", inputProtocolFactory);
			// connectionContext.setAttribute(ServerDef.KEY_PROTOCOL_FACTORY,
			// inputProtocolFactory);
			// Messages with a zero MSB in the first byte are framed messages
			return new ThriftMessage(messageBuffer, inputProtocolFactory).setWrapper(framedMessageWrapper(successor));
		}
	}

	private TProtocolFactory getProtocolFactory(ByteBuf buffer) {
		short headCode = buffer.getShort(buffer.readerIndex());
		if (headCode != 0 && buffer.readableBytes() > MESSAGE_FRAME_SIZE) {
			return serverDef.protocolFactorySelector.getProtocolFactory(headCode);
		}
		return null;
	}

	protected ByteBuf tryDecodeFramedMessage(ChannelHandlerContext ctx, Channel channel, ByteBuf buffer) {
		// Framed messages are prefixed by the size of the frame (which doesn't
		// include the
		// framing itself).

		int messageStartReaderIndex = buffer.readerIndex();
		int messageContentsOffset;

		// if (stripFraming) {
		messageContentsOffset = messageStartReaderIndex + MESSAGE_FRAME_SIZE;
		// } else {
		// messageContentsOffset = messageStartReaderIndex;
		// }

		// The full message is larger by the size of the frame size prefix
		int messageLength = buffer.getInt(messageStartReaderIndex) + MESSAGE_FRAME_SIZE;
		int messageContentsLength = messageStartReaderIndex + messageLength - messageContentsOffset;
		logger.debug("messageLength={}, rIndex={}, offset={}, readableBytes={}", messageLength, messageStartReaderIndex,
				messageContentsOffset, buffer.readableBytes());
		if (messageContentsLength > maxFrameSize) {
			throw new TooLongFrameException(
					String.format("Frame size exceeded on encode: frame was %d bytes, maximum allowed is %d bytes",
							messageLength, maxFrameSize));
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
			ByteBuf messageBuffer = extractFrame(buffer, messageContentsOffset, messageContentsLength);
			buffer.readerIndex(messageStartReaderIndex + messageLength);
			return messageBuffer;
		}
	}

	protected ByteBuf tryDecodeUnframedMessage(ChannelHandlerContext ctx, Channel channel, ByteBuf buffer,
			TProtocolFactory inputProtocolFactory) throws Exception {
		// Perform a trial decode, skipping through
		// the fields, to see whether we have an entire message available.

		int messageLength = 0;
		// record original readerIndex
		final int messageStartReaderIndex = buffer.readerIndex();

		try {
			TNettyTransport decodeAttemptTransport = new TNettyTransport(channel, buffer);
			int initialReadBytes = decodeAttemptTransport.getReadByteCount();
			TProtocol inputProtocol = inputProtocolFactory.getProtocol(decodeAttemptTransport);

			// Skip through the message
			inputProtocol.readMessageBegin();
			TProtocolUtil.skip(inputProtocol, TType.STRUCT);
			inputProtocol.readMessageEnd();

			messageLength = decodeAttemptTransport.getReadByteCount() - initialReadBytes;
		} catch (TTransportException e) {
			// No complete message was decoded: ran out of bytes
			return null;
		} catch (IndexOutOfBoundsException e) {
			// No complete message was decoded: ran out of bytes
			return null;
		} catch (TProtocolException e) {
			// No complete message was decoded: ran out of bytes
			return null;
		} finally {
			if (buffer.readerIndex() - messageStartReaderIndex > maxFrameSize) {
				throw new TooLongFrameException("Maximum frame size of " + maxFrameSize + " exceeded");
			}
			// reset messageStartReaderIndex to original
			buffer.readerIndex(messageStartReaderIndex);
		}

		if (messageLength <= 0) {
//			System.out.println("** 消息长度非法：" + messageLength);
			return null;
		}

		// We have a full message in the read buffer, slice it off
		ByteBuf messageBuffer = extractFrame(buffer, messageStartReaderIndex, messageLength);
		// set real readerIndex
		buffer.readerIndex(messageStartReaderIndex + messageLength);
//		System.out.println("** 返回完整消息");
		return messageBuffer;
	}

	protected ByteBuf extractFrame(ByteBuf buffer, int index, int length) {
		// Slice should be sufficient here (and avoids the copy in
		// LengthFieldBasedFrameDecoder)
		// because we know no one is going to modify the contents in the read
		// buffers.
		return buffer.slice(index, length);
	}

	private static final ThriftMessageWrapper UNFRAMED_WRAPPER = new ThriftMessageWrapper() {
		@Override
		public Object wrapMessage(ChannelHandlerContext ctx, ThriftMessage msg) {
			logger.debug("UNFRAMED_WRAPPER::wrapMessage");
			return msg.getContent();
		}
	};

	private static final ThriftMessageWrapper FRAMED_WRAPPER = new ThriftMessageWrapper() {
		@Override
		public void beforeMessageWrite(ChannelHandlerContext ctx, ThriftMessage msg) {
			ByteBuf buf = msg.getContent();
			buf.writerIndex(MESSAGE_FRAME_SIZE);
		}

		@Override
		public Object wrapMessage(ChannelHandlerContext ctx, ThriftMessage msg) {
			ByteBuf buf = msg.getContent();
			final int size = buf.readableBytes() - MESSAGE_FRAME_SIZE;
			final int writerIndex = buf.writerIndex();
			logger.debug("framedMessage::wrapMessage , size={}, buf.writeables={}", size, buf.writableBytes());
			buf.writerIndex(0);
			buf.writeInt(size);
			buf.writerIndex(writerIndex);
			return buf;
		}

	};

	private ThriftMessageWrapper unframedMessageWrapper(ThriftMessageWrapper successor) {
		if (successor == null) {
			return UNFRAMED_WRAPPER;
		}
		return new ThriftMessageWrapper(successor) {
			@Override
			public Object wrapMessageInner(ChannelHandlerContext ctx, ThriftMessage msg) {
				return UNFRAMED_WRAPPER.wrapMessage(ctx, msg);
			}
		};
	}

	private ThriftMessageWrapper framedMessageWrapper(ThriftMessageWrapper successor) {
		if (successor == null) {
			return FRAMED_WRAPPER;
		}
		return new ThriftMessageWrapper(successor) {
			@Override
			public void beforeMessageWrite(ChannelHandlerContext ctx, ThriftMessage msg) {
				FRAMED_WRAPPER.beforeMessageWrite(ctx, msg);
				getSuccessor().beforeMessageWrite(ctx, msg);
			}

			@Override
			protected Object wrapMessageInner(ChannelHandlerContext ctx, ThriftMessage msg) {
				return FRAMED_WRAPPER.wrapMessage(ctx, msg);
			}
		};
	}

}
