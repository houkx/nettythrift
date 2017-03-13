/**
 * 
 */
package io.nettythrift.core;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
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
	public void process(final ChannelHandlerContext ctx, TProtocol in, final TProtocol out, WriterHandler onComplete)
			throws Exception {
		final TMessage msg = in.readMessageBegin();
		final boolean filterBeforeRead = filterBeforeRead(ctx, msg);
		// ===== read in NIO thread =====
		final ProcessFunction fn = serverDef.processMap.get(msg.name);
		if (fn == null) {
			TProtocolUtil.skip(in, TType.STRUCT);
			in.readMessageEnd();
			TApplicationException x = new TApplicationException(TApplicationException.UNKNOWN_METHOD,
					"Invalid method name: '" + msg.name + "'");
			writeException(out, msg, onComplete, x, null);
			return;
		}
		final TBase args = fn.getEmptyArgsInstance();
		try {
			args.read(in);
		} catch (TProtocolException e) {
			in.readMessageEnd();
			TApplicationException x = new TApplicationException(TApplicationException.PROTOCOL_ERROR, e.getMessage());
			writeException(out, msg, onComplete, x, args);
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
			writeResult(out, msg, onComplete, args, null);
			Runnable task = new Runnable() {
				@SuppressWarnings("unchecked")
				@Override
				public void run() {
					long startTime = System.currentTimeMillis();
					boolean filterBeforeWrite = filterBeforeWrite(ctx, msg, args, filterBeforeRead);
					try {
						// ==== invoke user interface logics
						fn.getResult(serverDef.iface, args);
					} catch (Exception tex) {
						logger.error("Internal error processing " + msg.name, tex);
					} finally {
						serverDef.logicExecutionStatistics.saveExecutionMillTime(msg.name,
								(int) (System.currentTimeMillis() - startTime));
						filterAfterWrite(ctx, msg, null, filterBeforeWrite);
					}
				}
			};
			if (serverDef.logicExecutionStatistics.shouldExecuteInIOThread(msg.name)) {
				logger.debug("execute in IO thread: interface={}", msg);
				task.run();
			} else {
				sumbitTask(out, msg, onComplete, task);
			}
			return;
		}
		invokeAndWrite(ctx, out, msg, filterBeforeRead, fn, args, onComplete);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void invokeAndWrite(final ChannelHandlerContext ctx, final TProtocol out, final TMessage msg,
			final boolean filterBeforeRead, final ProcessFunction fn, final TBase args, final WriterHandler onComplete)
			throws InterruptedException, ExecutionException {

		if (serverDef.logicExecutionStatistics.shouldExecuteInIOThread(msg.name)) {
			logger.debug("execute in IO thread: interface={}", msg);
			long startTime = System.currentTimeMillis();
			boolean filterBeforeWrite = filterBeforeWrite(ctx, msg, args, filterBeforeRead);
			TBase result = null;
			try {
				// ==== invoke user interface logics
				result = fn.getResult(serverDef.iface, args);
			} catch (Throwable tex) {
				String error = "Internal error processing " + msg.name;
				logger.error(error, tex);
				final TApplicationException x = new TApplicationException(TApplicationException.INTERNAL_ERROR, error);
				writeException(out, msg, onComplete, x, args);
				return;
			} finally {
				serverDef.logicExecutionStatistics.saveExecutionMillTime(msg.name,
						(int) (System.currentTimeMillis() - startTime));
				filterAfterWrite(ctx, msg, result, filterBeforeWrite);
			}
			writeResult(out, msg, onComplete, args, result);
			return;
		}
		// invoke may be in a User Thread
		sumbitTask(out, msg, onComplete, new Runnable() {
			@Override
			public void run() {
				long startTime = System.currentTimeMillis();
				boolean filterBeforeWrite = filterBeforeWrite(ctx, msg, args, filterBeforeRead);
				TBase result = null;
				try {
					// ==== invoke user interface logics
					result = fn.getResult(serverDef.iface, args);
				} catch (Throwable tex) {
					String error = "Internal error processing " + msg.name;
					logger.error(error, tex);
					final TApplicationException x = new TApplicationException(TApplicationException.INTERNAL_ERROR,
							error);
					// switch to NIO thread to write
					ctx.executor().submit(new Runnable() {
						@Override
						public void run() {
							writeException(out, msg, onComplete, x, args);
						}
					});
					return;
				} finally {
					serverDef.logicExecutionStatistics.saveExecutionMillTime(msg.name,
							(int) (System.currentTimeMillis() - startTime));
					filterAfterWrite(ctx, msg, result, filterBeforeWrite);
				}
				final TBase RESULT = result;
				// switch to NIO thread to write
				ctx.executor().submit(new Runnable() {
					@Override
					public void run() {
						writeResult(out, msg, onComplete, args, RESULT);
					}
				});
			}
		});
	}

	@SuppressWarnings("rawtypes")
	private void writeResult(final TProtocol out, final TMessage msg, final WriterHandler onComplete, TBase args,
			final TBase result) {
		try {
			onComplete.beforeWrite(msg, args, result);
			// if (!isOneway()) {
			out.writeMessageBegin(new TMessage(msg.name, TMessageType.REPLY, msg.seqid));
			if (result != null) {
				result.write(out);
			} else {
				out.writeStructBegin(null);
				out.writeFieldStop();
				out.writeStructEnd();
			}
			out.writeMessageEnd();
			out.getTransport().flush();
			// }
			onComplete.afterWrite(msg, null, TMessageType.REPLY, args, result);
		} catch (Throwable e) {
			onComplete.afterWrite(msg, e, TMessageType.EXCEPTION, args, result);
		}
	}
	
	private void sumbitTask(TProtocol out, final TMessage msg, final WriterHandler onComplete, Runnable task){
		try {
			serverDef.executor.submit(task);
		} catch (RejectedExecutionException e) {
			TApplicationException x = new TApplicationException(TApplicationException.INTERNAL_ERROR, "TooBusy");
			writeException(out, msg, onComplete, x, null);
			logger.error("RejectedExecutionException: "+e.getLocalizedMessage());
		}
	}

	@SuppressWarnings({ "rawtypes" })
	private void writeException(final TProtocol out, final TMessage msg, final WriterHandler onComplete,
			final TApplicationException x, TBase args) {
		Throwable cause = null;
		try {
			onComplete.beforeWrite(msg, args, null);
			out.writeMessageBegin(new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid));
			x.write(out);
			out.writeMessageEnd();
			out.getTransport().flush();
		} catch (Throwable e) {
			cause = e;
		}
		onComplete.afterWrite(msg, cause, TMessageType.EXCEPTION, args, null);
	}
}
