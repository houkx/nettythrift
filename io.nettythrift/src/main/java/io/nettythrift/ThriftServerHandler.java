/**
 * 
 */
package io.nettythrift;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.nettythrift.transport.TNiftyTransport;
import io.nettythrift.transport.ThriftTransportType;

/**
 * @author HouKangxi
 *
 */
public class ThriftServerHandler extends SimpleChannelInboundHandler<ThriftMessage> {
	private final ServerConfig serverDef;

	public ThriftServerHandler(ServerConfig serverDef) {
		this.serverDef = serverDef;
	}

	class NioWriterFlusherImpl implements NioWriterFlusher {
		final ChannelHandlerContext ctx;
		final ThriftMessage message;
		final TNiftyTransport messageTransport;

		public NioWriterFlusherImpl(ChannelHandlerContext ctx, ThriftMessage message,
				TNiftyTransport messageTransport) {
			this.ctx = ctx;
			this.message = message;
			this.messageTransport = messageTransport;
		}

		@Override
		public io.netty.util.concurrent.EventExecutor handlerContextExecutor() {
			return ctx.executor();
		}

		@Override
		public void doFlush(int code, String respMessage) {
			Object resp;
			ChannelFutureListener lis = ChannelFutureListener.CLOSE_ON_FAILURE;
			if (message.getTransportType() == ThriftTransportType.HTTP) {
				HttpVersion version = new HttpVersion("HTTP/1.1", false);
				HttpResponseStatus status = new HttpResponseStatus(message.fromProgram ? 200 : code, respMessage);
				DefaultFullHttpResponse httpResp = new DefaultFullHttpResponse(version, status, messageTransport.getOutputBuffer());
				resp = httpResp;
				if (!message.connectionKeepAlive || !message.fromProgram/*浏览器的keep-alive暂时忽略*/) {
					lis = ChannelFutureListener.CLOSE;
				}
			}else{
				ThriftMessage msg = message.clone(messageTransport.getOutputBuffer());
				msg.responseCode = code;
				msg.responseMessage = respMessage;
				resp = msg;
			}
			ctx.writeAndFlush(resp).addListener(lis);
		}

		@Override
		public ThriftTransportType transportType() {
			return message.getTransportType();
		}
	}

	@Override
	protected void messageReceived(final ChannelHandlerContext ctx, final ThriftMessage message) throws Exception {
		// System.out.printf("ThriftServerHandler: channelRead0( message = %s),
		// readable? %s, frameSize = %d\n" , message
		// ,message.getBuffer().isReadable(),message.getBuffer().readableBytes());
		if (message.readResult != null) {
			final TNiftyTransport messageTransport = new TNiftyTransport(ctx.channel(), null, TNiftyTransport.MOD_W);
			TProtocolFactory proctocolFactory = message.getProctocolFactory();
			TProtocol outProtocol = proctocolFactory.getProtocol(messageTransport);
			NioWriterFlusherImpl flusher = new NioWriterFlusherImpl(ctx, message, messageTransport);
			serverDef.getProcessor().write(flusher, outProtocol, serverDef, message.getProxyInfo(), message.readResult);
			return;
		}
		TNiftyTransport msgTrans = new TNiftyTransport(ctx.channel(), message.getBuffer(), TNiftyTransport.MOD_RW);
		TProtocolFactory proctocolFactory = message.getProctocolFactory();
		TProtocol protocol = proctocolFactory.getProtocol(msgTrans);

		processRequest(ctx, message, msgTrans, protocol, protocol);
	}

	private void processRequest(final ChannelHandlerContext ctx, final ThriftMessage message,
			final TNiftyTransport messageTransport, final TProtocol inProtocol, final TProtocol outProtocol) {
		// final int requestSequenceId = dispatcherSequenceId.incrementAndGet();
		try {
			NioWriterFlusherImpl flusher = new NioWriterFlusherImpl(ctx, message, messageTransport);
			serverDef.getProcessor().process(flusher, inProtocol, outProtocol, serverDef, message.getProxyInfo());
		} catch (TException e) {
			e.printStackTrace();
		}
	}
}
