/**
 * 
 */
package io.nettythrift;

import static io.nettythrift.ResponseCodes.CODE_NOT_ACCEPT;
import static io.nettythrift.ResponseCodes.CODE_REQ_TIMEOUT;
import static io.nettythrift.ResponseCodes.CODE_RES_NOTFOUND;
import static io.nettythrift.ResponseCodes.CODE_SERVER_INTERNAL_ERR;
import static io.nettythrift.ResponseCodes.CODE_SUCCESS;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseProcessor;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.nettythrift.protocol.SimpleJSONProtocol;
import io.nettythrift.transport.ThriftTransportType;

/**
 * @author houkangxi
 *
 */
@SuppressWarnings("rawtypes")
public class NioProcessor<I> {
	private static final Logger LOGGER = LoggerFactory.getLogger(NioProcessor.class);

	private final Map<String, ProcessFunction<I, ? extends TBase>> processMap;
	private I iface;
	private final ExecutorService executor;
	private final HashMap<Byte, TProtocolFactory> protocolFactoryMap = new HashMap<Byte, TProtocolFactory>(8);

	@SuppressWarnings("unchecked")
	public NioProcessor(TBaseProcessor<I> targetProcessor, ExecutorService executor) {
		this.executor = executor;
		processMap = targetProcessor.getProcessMapView();
		try {
			Field f = TBaseProcessor.class.getDeclaredField("iface");
			f.setAccessible(true);
			iface = (I) f.get(targetProcessor);
		} catch (Exception e) {
			e.printStackTrace();
		}
		protocolFactoryMap.put((byte) 0x80, new TBinaryProtocol.Factory());
		protocolFactoryMap.put((byte) 0x82, new TCompactProtocol.Factory());
		protocolFactoryMap.put((byte) 91, new TJSONProtocol.Factory());
		Class<?>[] ifcs = iface.getClass().getInterfaces();
		Class ifaceClass = null;
		for (Class c : ifcs) {
			if (c.getEnclosingClass() != null && c.getSimpleName().equals("Iface")) {
				ifaceClass = c;
				break;
			}
		}
		if (ifaceClass == null) {
			LOGGER.warn(" ifaceClass = null !");
		} else {
			LOGGER.debug(" ifaceClass = {}", ifaceClass);
		}
		protocolFactoryMap.put((byte) 92, new SimpleJSONProtocol.Factory(ifaceClass));
	}

	TProtocolFactory getProtocolFactory(ByteBuf buf) throws TException {
		byte headByte = buf.getByte(0);
		// SimpleJson的前两个字符为：[" ，而TJSONProtocol的第二个字符为一个数字
		if (headByte == 91 && buf.capacity() > 1 && buf.getByte(1) == 34) {
			headByte = 92;
		}
		TProtocolFactory fac = protocolFactoryMap.get(headByte);
		if (fac != null) {
			return fac;
		}
		// throw new TException("Unkown protocolType: " + headByte);
		return null;
	}

	public static class ReadResult {
		final TMessage msg;
		final TBase args;
		final ProcessFunction fn;

		private ReadResult(TMessage msg, TBase args, ProcessFunction fn) {
			this.msg = msg;
			this.args = args;
			this.fn = fn;
		}

	}

	 ReadResult read(final NioWriterFlusher ctx, TProtocol in, final TProtocol out, ServerConfig serverDef,
			String proxyInfo) throws TException {
		final TMessage msg = in.readMessageBegin();
		final ProcessFunction fn = processMap.get(msg.name);
		if (fn == null) {
			if (msg.type == TMessageType.CALL) {
				TProtocolUtil.skip(in, TType.STRUCT);
				in.readMessageEnd();
			}
			TApplicationException x = invalidMethodException(msg);
			int code = ctx.transportType() == ThriftTransportType.HTTP ? CODE_RES_NOTFOUND : x.getType();
			writeOut(ctx, out, new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid), new TAppExceptionTBase(x),
					code, x.getMessage());
			return null;
		}
		final TBase args = fn.getEmptyArgsInstance();
		try {
			args.read(in);
		} catch (TProtocolException e) {
			in.readMessageEnd();
			TApplicationException x = protocolExceptionWhenRead(msg, e);
			int code = ctx.transportType() == ThriftTransportType.HTTP ? CODE_NOT_ACCEPT : x.getType();
			writeOut(ctx, out, new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid), new TAppExceptionTBase(x),
					code, x.getMessage());
			return null;
		}
		in.readMessageEnd();
		// process proxyInfo
		if (proxyInfo != null && serverDef.getProxyHandler() != null) {
			LOGGER.debug("set proxyInfo:{}, proxyHandler={}", proxyInfo, serverDef.getProxyHandler());
			serverDef.getProxyHandler().handlerProxyInfo(args, proxyInfo);
		} else {
			LOGGER.warn("proxyInfo={}, ProxyHandler={}", proxyInfo, serverDef.getProxyHandler());
		}
		return new ReadResult(msg, args, fn);
	}

	 void write(final NioWriterFlusher ctx, final TProtocol out, ServerConfig serverDef,
			String proxyInfo, final ReadResult readResult) throws TException {
		if (readResult == null) {
			return;
		}
		final TMessage msg = readResult.msg;
		// process taskTimeOut
		final java.util.concurrent.ScheduledFuture timeOutResponseFuture;
		timeOutResponseFuture = procTaskTimeOut(ctx, out, serverDef, msg);
		// 在用户线程执行业务逻辑
		executor.submit(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				TBase result = null;
				byte msgType;
				int respCode;
				String respMsg;
				try {
					result = readResult.fn.getResult(iface, readResult.args);
					msgType = TMessageType.REPLY;
					respCode = CODE_SUCCESS;
					respMsg = "OK";
				} catch (TException tex) {
					final TApplicationException ex = internalException(msg, tex);
					respMsg = ex.getMessage();
					result = new TAppExceptionTBase(ex);
					msgType = TMessageType.EXCEPTION;
					respCode = ctx.transportType() == ThriftTransportType.HTTP ? CODE_SERVER_INTERNAL_ERR
							: ex.getType();
				}
				if (timeOutResponseFuture == null || timeOutResponseFuture.cancel(true)) {
					LOGGER.debug("任务response try 正常返回");
					// 提交到IO线程执行写入
					final TBase _result = result;
					final byte _msgType = msgType;
					final int _respCode = respCode;
					final String _respMsg = respMsg;
					ctx.handlerContextExecutor().execute(new Runnable() {
						@Override
						public void run() {
							try {
								TMessage resultMsg = new TMessage(msg.name, _msgType, msg.seqid);
								writeOut(ctx, out, resultMsg, _result, _respCode, _respMsg);
							} catch (TException e) {
								e.printStackTrace();
							}
						}
					});
				} else {
					LOGGER.warn("任务执行完毕，但已经超时。");
				}
			}
		});
	}

	private java.util.concurrent.ScheduledFuture procTaskTimeOut(final NioWriterFlusher ctx, final TProtocol out,
			ServerConfig serverDef, final TMessage msg) {
		final java.util.concurrent.ScheduledFuture timeOutResponseFuture;
		if (serverDef.getTaskTimeoutMillis() > 0) {
			timeOutResponseFuture = ctx.handlerContextExecutor().schedule(new Runnable() {
				@Override
				public void run() {
					LOGGER.warn("任务已超时");
					TApplicationException x = taskTimeOutException(msg);
					int code = ctx.transportType() == ThriftTransportType.HTTP ? CODE_REQ_TIMEOUT : x.getType();
					try {
						TMessage resultMsg = new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid);
						writeOut(ctx, out, resultMsg, new TAppExceptionTBase(x), code, x.getMessage());
					} catch (TException e) {
						e.printStackTrace();
					}
				}
			}, serverDef.getTaskTimeoutMillis(), TimeUnit.MILLISECONDS);
		} else {
			LOGGER.warn("未设置任务超时时间");
			timeOutResponseFuture = null;
		}
		return timeOutResponseFuture;
	}

	public void process(final NioWriterFlusher ctx, TProtocol in, final TProtocol out, ServerConfig serverDef,
			String proxyInfo) throws TException {
		// 由IO 线程发起和执行读取，即解码操作。
		ReadResult readResult = read(ctx, in, out, serverDef, proxyInfo);
		write(ctx, out, serverDef, proxyInfo, readResult);
	}

	protected TApplicationException invalidMethodException(TMessage msg) {
		return new TApplicationException(TApplicationException.UNKNOWN_METHOD,
				"Invalid method name: '" + msg.name + "'");
	}

	protected TApplicationException protocolExceptionWhenRead(TMessage msg, TProtocolException e) {
		return new TApplicationException(TApplicationException.PROTOCOL_ERROR, e.getMessage());
	}

	protected TApplicationException taskTimeOutException(TMessage msg) {
		return new TApplicationException(TApplicationException.INTERNAL_ERROR, "ServerInternalTimeOut");
	}

	protected TApplicationException internalException(TMessage msg, TException tex) {
		String desc = "Internal error processing " + msg.name;
		LOGGER.error(desc, tex);
		return new TApplicationException(TApplicationException.INTERNAL_ERROR, desc);
	}

	private void writeOut(final NioWriterFlusher ctx, final TProtocol out, final TMessage resultMsg,
			final TBase _result, int code, String respMsg) throws TException, TTransportException {
		out.writeMessageBegin(resultMsg);
		_result.write(out);
		out.writeMessageEnd();
		out.getTransport().flush();
		ctx.doFlush(code, respMsg);
	}
}
