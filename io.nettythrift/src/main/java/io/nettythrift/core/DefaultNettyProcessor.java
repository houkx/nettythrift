/**
 * 
 */
package io.nettythrift.core;

import java.util.concurrent.ExecutionException;

import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

/**
 * @author HouKx
 *
 */
public class DefaultNettyProcessor implements NettyProcessor {
	private static Logger logger = LoggerFactory.getLogger(DefaultNettyProcessor.class);
	private final ThriftServerDef serverDef;

	public DefaultNettyProcessor(ThriftServerDef serverDef) {
		this.serverDef = serverDef;
	}
	
	// NOTE: invoked in IO thread
	protected boolean filterBeforeRead(ChannelHandlerContext ctx, TMessage msg) {
		return false;
	}
	
	// NOTE: invoked in business thread
	protected boolean filterBeforeWrite(ChannelHandlerContext ctx, TMessage msg,
			@SuppressWarnings("rawtypes") TBase args, boolean filterBeforeRead) {
		return false;
	}

	// NOTE: invoked in business thread
	protected boolean filterAfterWrite(ChannelHandlerContext ctx, TMessage msg,
			@SuppressWarnings("rawtypes") TBase result, boolean filterBeforeWrite) {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.netty5thrift.core.NettyProcessor#process(io.netty.channel.
	 * ChannelHandlerContext, org.apache.thrift.protocol.TProtocol,
	 * org.apache.thrift.protocol.TProtocol)
	 */
	@Override
	@SuppressWarnings({ "rawtypes" })
	public void process(final ChannelHandlerContext ctx, TProtocol in, final TProtocol out, WriteListener onComplete)
			throws Exception {
		final TMessage msg = in.readMessageBegin();
		final boolean filterBeforeRead = filterBeforeRead(ctx, msg);
		// ===== read in NIO thread =====
		final ProcessFunction fn = serverDef.processMap.get(msg.name);
		if (fn == null) {
			TProtocolUtil.skip(in, TType.STRUCT);
			in.readMessageEnd();
			onComplete.beforeWrite(msg);
			TApplicationException x = new TApplicationException(TApplicationException.UNKNOWN_METHOD,
					"Invalid method name: '" + msg.name + "'");
			out.writeMessageBegin(new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid));
			x.write(out);
			out.writeMessageEnd();
			out.getTransport().flush();
			onComplete.afterWrite(msg, x, TMessageType.EXCEPTION);
			return;
		}
		final TBase args = fn.getEmptyArgsInstance();
		try {
			args.read(in);
		} catch (TProtocolException e) {
			in.readMessageEnd();
			onComplete.beforeWrite(msg);
			TApplicationException x = new TApplicationException(TApplicationException.PROTOCOL_ERROR, e.getMessage());
			out.writeMessageBegin(new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid));
			x.write(out);
			out.writeMessageEnd();
			out.getTransport().flush();
			onComplete.afterWrite(msg, e, TMessageType.EXCEPTION);
			return;
		}
		in.readMessageEnd();
		//
		// === read request end ===
		//
		// if a method return 'void', try directReturn,this could reduce a
		// ThreadSwitch (from UserThread to NIOThread), but exception throws by
		// voidMethod is ignored if it(voidMethod) throws Exception in fact.
		//
		if (serverDef.voidMethodDirectReturn && serverDef.isVoidMethod(msg.name)) {
			logger.debug("direct return for voidMethod: {}", msg.name);
			onComplete.beforeWrite(msg);
			out.writeMessageBegin(new TMessage(msg.name, TMessageType.REPLY, msg.seqid));
			//
			out.writeStructBegin(null);
			out.writeFieldStop();
			out.writeStructEnd();
			//
			out.writeMessageEnd();
			out.getTransport().flush();
			onComplete.afterWrite(msg, null, TMessageType.REPLY);
			serverDef.executor.submit(new Runnable() {
				@SuppressWarnings("unchecked")
				@Override
				public void run() {
					boolean filterBeforeWrite = filterBeforeWrite(ctx, msg, args, filterBeforeRead);
					try {
						// ==== invoke user interface logics
						fn.getResult(serverDef.iface, args);
					} catch (final TException tex) {
						logger.error("Internal error processing " + msg.name, tex);
					} finally {
						filterAfterWrite(ctx, msg, null, filterBeforeWrite);
					}
				}
			});
			return;
		}
		invokeAndWrite(ctx, out, msg, filterBeforeRead, fn, args, onComplete);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void invokeAndWrite(final ChannelHandlerContext ctx, final TProtocol out, final TMessage msg,
			final boolean filterBeforeRead, final ProcessFunction fn, final TBase args, final WriteListener onComplete)
			throws InterruptedException, ExecutionException {
		// invoke may be in a User Thread
		serverDef.executor.submit(new Runnable() {
			@Override
			public void run() {
				boolean filterBeforeWrite = filterBeforeWrite(ctx, msg, args, filterBeforeRead);
				TBase result = null;
				try {
					// ==== invoke user interface logics
					result = fn.getResult(serverDef.iface, args);
				} catch (final TException tex) {
					logger.error("Internal error processing " + msg.name, tex);
					final TApplicationException x = new TApplicationException(TApplicationException.INTERNAL_ERROR,
							"Internal error processing " + msg.name);
					// switch to NIO thread to write
					ctx.executor().submit(new Runnable() {
						@Override
						public void run() {
							Throwable cause = null;
							try {
								onComplete.beforeWrite(msg);
								out.writeMessageBegin(new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid));
								x.write(out);
								out.writeMessageEnd();
								out.getTransport().flush();
							} catch (Throwable e) {
								cause = e;
							}
							onComplete.afterWrite(msg, cause, TMessageType.EXCEPTION);
						}
					});
					return;
				} finally {
					filterAfterWrite(ctx, msg, result, filterBeforeWrite);
				}
				final TBase RESULT = result;
				// switch to NIO thread to write
				ctx.executor().submit(new Runnable() {
					@Override
					public void run() {
						try {
							onComplete.beforeWrite(msg);
							// if (!isOneway()) {
							out.writeMessageBegin(new TMessage(msg.name, TMessageType.REPLY, msg.seqid));
							if (RESULT != null) {
								RESULT.write(out);
							}
							out.writeMessageEnd();
							out.getTransport().flush();
							// }
							onComplete.afterWrite(msg, null, TMessageType.REPLY);
						} catch (Throwable e) {
							onComplete.afterWrite(msg, e, TMessageType.EXCEPTION);
						}
					}
				});
			}
		});
	}

}
