/**
 * 
 */
package io.nettythrift;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author HouKangxi
 *
 */
public class ThriftMessageEncoder extends MessageToByteEncoder<ThriftMessage> {
	private final Logger LOGGER = LoggerFactory.getLogger(getClass().getName());
	private final long maxFrameSize;

	public ThriftMessageEncoder(long maxFrameSize) {
		this.maxFrameSize = maxFrameSize;
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ThriftMessage message, ByteBuf out)
			throws Exception {
		
		ByteBuf buf = message.getBuffer();
		int frameSize = buf.readableBytes();
//		System.out.printf("%s::encode: message = %s, buf.isReadable? %s, frameSize=%d\n",
//				getClass().getSimpleName(),message, buf.isReadable(), frameSize);
		if (message.getBuffer().readableBytes() > maxFrameSize) {
			throw new TooLongFrameException(
					String.format(
							"Frame size exceeded on encode: frame was %d bytes, maximum allowed is %d bytes",
							frameSize, maxFrameSize));
		}

		switch (message.getTransportType()) {
		case UNFRAMED: {
			out.writeBytes(buf);
			break;
		}
		case FRAMED: {
			out.writeInt(frameSize);
			out.writeBytes(buf);
			break;
		}
		case HEADER:
			throw new UnsupportedOperationException("Header transport is not supported");

		case HTTP: {
			HttpVersion version = new HttpVersion("HTTP/1.1", false);
			HttpResponseStatus status = new HttpResponseStatus(message.responseCode, message.responseMessage);
			ByteBuf content = buf;
			DefaultFullHttpResponse httpResp = new DefaultFullHttpResponse(version,
					status, content);
			ctx.write(httpResp);
			break;
		}

		default:
			throw new UnsupportedOperationException("Unrecognized transport type");
		}
	}

}
