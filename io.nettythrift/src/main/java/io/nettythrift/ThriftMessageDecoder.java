/**
 * 
 */
package io.nettythrift;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.concurrent.ScheduledFuture;
import io.nettythrift.transport.TNiftyTransport;
import io.nettythrift.transport.ThriftTransportType;

/**
 * decode a ByteBuf to ThriftMessage
 * 
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
		if (ctx.channel().isActive()) {

			Object msg = decode(ctx, in);
			if (msg != null) {
				out.add(msg);
			}
			if (in.refCnt() > 0) {
				lastMsg = in.retain();
			}
		}
	}

	private String proxyInfo;
	private boolean notifyNextHandler;
	private boolean isHttp;
	private boolean unframe;
	private TProtocolFactory fac = null;
	private boolean ignoreReadcomplete;
	private int widx = 0, cap = 0;
	private ByteBuf lastMsg;
	private int offset = 0, msgLen = 0;
	private ScheduledFuture<?> readTaskFuture;

	private void reset() {
		notifyNextHandler = false;
		isHttp = false;
		unframe = false;
		ignoreReadcomplete = false;
		widx = cap = 0;
		lastMsg = null;
		offset = msgLen = 0;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (readTaskFuture != null) {
			readTaskFuture.cancel(true);
		}
		if (msg instanceof ByteBuf) {
			ByteBuf buffer = (ByteBuf) msg;
			widx = buffer.writerIndex();
			cap = buffer.capacity();
		}
		logger.debug("@{}:: channelRead:msg={}", System.identityHashCode(this), msg);
		super.channelRead(ctx, msg);
	}

	private Object decode(final ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
		// return super.decode(ctx, in);
		if (!buffer.isReadable()) {
			return null;
		}
		if (proxyInfo == null && proxyHandler != null) {
			logger.debug("@{}:: 尝试读取解析proxy", System.identityHashCode(this));
			int oldReadableBytes = buffer.readableBytes();
			proxyInfo = proxyHandler.getHeadProxyInfo(buffer);
			if (buffer.readableBytes() != oldReadableBytes) {
				ignoreReadcomplete = true;
			}
			if (!buffer.isReadable()) {
				return null;
			}
		}

		if (lastMsg == null) {
			short firstByte = buffer.getUnsignedByte(buffer.readerIndex());
			logger.debug("[@{}]:: decode():firstByte = {},len={}", System.identityHashCode(this), firstByte,
					buffer.readableBytes());
			if (firstByte == 80) {
				logger.debug("httpRequest from program.");
				notifyHttpDecoder(ctx, buffer, proxyInfo, true);
				return null;
			}
			if (firstByte == 71) {// 从浏览器访问时首字符是71
				logger.debug("HttpRequest from brower.");
				notifyHttpDecoder(ctx, buffer, proxyInfo, false);
				return null;
			}

			TProtocolFactory fac = null;
			if ((fac = serverDef.getProcessor().getProtocolFactory(buffer)) != null) {
				logger.debug("@{}::TTProtocolFactory = {} when UnframedMessage ,proxyInfo={}",
						System.identityHashCode(this), fac, proxyInfo);
				isHttp = false;
				notifyNextHandler = true;
				unframe = true;
				this.fac = fac;
				return null;
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
				// Messages with a zero MSB in the first byte are framed
				// messages
				return new ThriftMessage(messageBuffer, ThriftTransportType.FRAMED).setProctocolFactory(factory)
						.setProxyInfo(proxyInfo).setConnectionKeepAlive(true);
			}
		}
		logger.debug("[@{}]:: decode(): len={},cap={},widx={}", System.identityHashCode(this), buffer.readableBytes(),
				cap, widx);
		return null;
	}

	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		logger.debug("[@{}]:: channelInactive()", System.identityHashCode(this));
		super.channelInactive(ctx);
	}

	@Override
	public void channelReadComplete(final ChannelHandlerContext ctx) throws Exception {
		logger.debug("[@{}]:: channelReadComplete():  notifyNextHandler={}, widx={}, cap={}, active? {}",
				System.identityHashCode(this), notifyNextHandler, widx, cap, ctx.channel().isActive());
		super.channelReadComplete(ctx);
		if (ignoreReadcomplete || !ctx.channel().isActive()) {
			reset();
			return;
		}
		if (notifyNextHandler && lastMsg != null && cap > 0) {
			if (isHttp) {
				if (widx != cap) {
					ctx.fireChannelRead(lastMsg);
				} else {
					readTaskFuture = ctx.executor().schedule(new Runnable() {
						public void run() {
							ctx.fireChannelRead(lastMsg);
						}
					}, 10, TimeUnit.MILLISECONDS);
					return;
				}
			} else if (unframe) {
				if (widx != cap) {
					Object msg = directReadUnframedMessage(ctx, lastMsg, fac);
					logger.debug("[@{}]:: channelReadComplete(): unframe msg={}", System.identityHashCode(this), msg);
					if (msg != null) {
						ctx.fireChannelRead(msg);
					}
				} else {
					readTaskFuture = ctx.executor().schedule(new Runnable() {
						public void run() {
							Object msg = null;
							try {
								msg = directReadUnframedMessage(ctx, lastMsg, fac);
							} catch (TException e) {
								e.printStackTrace();
							}
							logger.debug("[@{}]::channelReadComplete():unframedMsg={}", System.identityHashCode(ThriftMessageDecoder.this),
									msg);
							if (msg != null) {
								ctx.fireChannelRead(msg);
							}
						}
					}, 10, TimeUnit.MILLISECONDS);
					return;
				}
			} else {
				if (offset > lastMsg.readableBytes() - msgLen) {
					return;
				}
				lastMsg = lastMsg.slice(offset, msgLen).retain();
				TProtocolFactory factory = serverDef.getProcessor().getProtocolFactory(lastMsg);
				logger.debug("[@{}]:: channelReadComplete(): TTProtocolFactory = {} when FramedMessage ",
						System.identityHashCode(this), factory);
				// Messages with a zero MSB in the first byte are framed
				// messages
				ThriftMessage msg = new ThriftMessage(lastMsg, ThriftTransportType.FRAMED).setProctocolFactory(factory)
						.setProxyInfo(proxyInfo).setConnectionKeepAlive(true);
				if (msg != null) {
					ctx.fireChannelRead(msg);
				}
			}
		}
		reset();
	}

	private Object directReadUnframedMessage(final ChannelHandlerContext ctx, ByteBuf buffer, TProtocolFactory fac)
			throws TException {
		final TNiftyTransport msgTrans = new TNiftyTransport(ctx.channel(), buffer, TNiftyTransport.MOD_RW);
		TProtocol in = fac.getProtocol(msgTrans);
		TProtocol out = in;
		final ThriftMessage msg = new ThriftMessage(null, ThriftTransportType.UNFRAMED).setProctocolFactory(fac)
				.setProxyInfo(proxyInfo).setConnectionKeepAlive(true);
		msg.readResult = serverDef.getProcessor().read(new NioWriterFlusher() {
			@Override
			public ThriftTransportType transportType() {
				return ThriftTransportType.UNFRAMED;
			}

			@Override
			public ScheduledExecutorService handlerContextExecutor() {
				return ctx.executor();
			}

			@Override
			public void doFlush(int code, String message) {
				ThriftMessage response = msg.clone(msgTrans.getOutputBuffer());
				response.responseCode = msg.responseCode = code;
				response.responseMessage = msg.responseMessage = message;
				ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
			}
		}, in, out, serverDef, proxyInfo);

		if (msg.responseCode != 0) {
			return null;
		}
		return msg;
	}

	private void notifyHttpDecoder(ChannelHandlerContext ch, ByteBuf buffer, String proxyInfo, boolean fromProgram) {
		notifyNextHandler = true;
		isHttp = true;
		ch.pipeline().addAfter("msgDecoder", "httpReqDecoder", new HttpRequestDecoder());
		ch.pipeline().addAfter("httpReqDecoder", "httpRespEncoder", new HttpResponseEncoder());
		ch.pipeline().addAfter("httpRespEncoder", "httpAggegator", new HttpObjectAggregator(512 * 1024));
		ch.pipeline().addAfter("httpAggegator", "httpReq2ThriftMsgDecoder",
				new HttpReq2MsgDecoder(serverDef, proxyInfo, fromProgram));
		ch.fireChannelRead(buffer.retain());// 往下个ChannelInBoundHandler 传递
	}

	private ByteBuf tryDecodeFramedMessage(ChannelHandlerContext ctx, ByteBuf buffer, boolean stripFraming) {
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
		logger.debug("*** FramedMsg: messageLength = {}, ridx={}, offset={}, msgContentLen={}, bufCap={}",
				messageLength, messageStartReaderIndex, messageContentsOffset, messageContentsLength,
				buffer.readableBytes());
		if (messageContentsLength > maxFrameSize) {
			throw new TooLongFrameException("Maximum frame size of " + maxFrameSize + " exceeded");
		}

		if (messageLength == 0) {
			// Zero-sized frame: just ignore it and return nothing
			buffer.readerIndex(messageContentsOffset);
			return null;
		} else if (buffer.readableBytes() < messageLength) {
			notifyNextHandler = true;
			offset = messageContentsOffset;
			msgLen = messageContentsLength;
			logger.error("*** buffer.readableBytes() = {}, but messageLength={}", buffer.readableBytes(),
					messageLength);
			// Full message isn't available yet, return nothing for now
			return null;
		} else {
			// Full message is available, return it
			ByteBuf messageBuffer = buffer.slice(messageContentsOffset, messageContentsLength).retain();
			buffer.readerIndex(messageStartReaderIndex + messageLength);
			return messageBuffer;
		}
	}
}
