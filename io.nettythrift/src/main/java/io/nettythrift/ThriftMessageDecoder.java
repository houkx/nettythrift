/**
 * 
 */
package io.nettythrift;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
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
	private List<ByteBuf> buflist = new ArrayList<ByteBuf>(8);
	private boolean notifyNextHandler;

	private void reset() {
		buflist = new ArrayList<ByteBuf>(8);
		notifyNextHandler = false;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		logger.debug("{}:: channelRead:msg={}", this, msg);
		super.channelRead(ctx, msg);
	}

	private Object decode(final ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
		// return super.decode(ctx, in);
		if (!buffer.isReadable()) {
			return null;
		}
		if (proxyInfo == null && proxyHandler != null) {
			logger.debug("{}:: 尝试读取解析proxy", this);
			proxyInfo = proxyHandler.getHeadProxyInfo(buffer);
			if (!buffer.isReadable()) {
				return null;
			}
		}

		buflist.add(buffer.retain());
		if (buflist.size() == 1) {
			short firstByte = buffer.getUnsignedByte(buffer.readerIndex());
			logger.debug("[{}]:: decode():firstByte = {},len={}", this + "-" + ctx.channel(), firstByte,
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
				logger.debug("{}::TTProtocolFactory = {} when UnframedMessage ,proxyInfo={}", this, fac, proxyInfo);
				return directReadUnframedMessage(ctx, buffer, fac);
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
						.setProxyInfo(proxyInfo);
			}
		}
		logger.debug("[{}]:: decode(): len={}", this + "-" + ctx.channel(), buffer.readableBytes());
		ctx.fireChannelRead(buffer);
		return null;
	}

	private Object directReadUnframedMessage(final ChannelHandlerContext ctx, ByteBuf buffer, TProtocolFactory fac)
			throws TException {
		final TNiftyTransport msgTrans = new TNiftyTransport(ctx.channel(), buffer, TNiftyTransport.MOD_RW);
		TProtocol in = fac.getProtocol(msgTrans);
		TProtocol out = in;
		final ThriftMessage msg = new ThriftMessage(null, ThriftTransportType.UNFRAMED).setProctocolFactory(fac)
				.setProxyInfo(proxyInfo);
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

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		logger.debug("[{}]:: channelReadComplete(): bufCount={}", this + "-" + ctx.channel(), buflist.size());
		super.channelReadComplete(ctx);
		if (notifyNextHandler && buflist.size() > 1) {
			ByteBuf totalBuf = Unpooled.wrappedBuffer(buflist.toArray(new ByteBuf[0]));
			ctx.fireChannelRead(totalBuf);
		}
		reset();
	}

	private void notifyHttpDecoder(ChannelHandlerContext ch, ByteBuf buffer, String proxyInfo, boolean fromProgram) {
		notifyNextHandler = true;
		ch.pipeline().addAfter("msgDecoder", "httpReqDecoder", new HttpRequestDecoder());
		ch.pipeline().addAfter("httpReqDecoder", "httpRespEncoder", new HttpResponseEncoder());
		ch.pipeline().addAfter("httpRespEncoder", "httpAggegator", new HttpObjectAggregator(512 * 1024));
		ch.pipeline().addAfter("httpAggegator", "httpReq2ThriftMsgDecoder",
				new HttpReq2MsgDecoder(serverDef, proxyInfo, fromProgram));
		ch.fireChannelRead(buffer);// 往下个ChannelInBoundHandler 传递
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

	protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
		return buffer.slice(index, length).retain();
	}
}
