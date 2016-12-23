/*
 * Copyright (C) 2012-2013 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nettythrift.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.thrift.TBaseProcessor;

import io.netty.channel.ChannelHandler;
import io.netty.util.concurrent.DefaultExecutorServiceFactory;
import io.nettythrift.protocol.DefaultProtocolFactorySelectorFactory;
import io.nettythrift.protocol.ProtocolFactorySelectorFactory;

/**
 * Builder for the Thrift Server descriptor. Example : <code>
 * new ThriftServerDefBuilder()
 * .listen(config.getServerPort())
 * .limitFrameSizeTo(config.getMaxFrameSize())
 * .withProcessor(new FacebookService.Processor(new MyFacebookBase()))
 * .using(Executors.newFixedThreadPool(5))
 * .build();
 * <p/>
 * <p/>
 * You can then pass ThriftServerDef to guice via a multibinder.
 * <p/>
 * </code>
 */
public abstract class ThriftServerDefBuilderBase<T extends ThriftServerDefBuilderBase<T>> {
	private static final AtomicInteger ID = new AtomicInteger(1);

	private String name = "netty5thrift-" + ID.getAndIncrement();
	private int serverPort = 8081;
	private int maxFrameSize = MAX_FRAME_SIZE;
	private int maxConnections;
	private int queuedResponseLimit = 16;
	@SuppressWarnings("rawtypes")
	private TBaseProcessor processor;
	private NettyProcessorFactory nettyProcessorFactory;// hasDefault
	private ChannelHandler contextHandlerInstaller;// hasDefault
	private ExecutorService executor;// hasDefault
	private long clientIdleTimeout = 15000;// hasDefault
	private ProtocolFactorySelectorFactory protocolFactorySelectorFactory;// hasDefault
	private HttpResourceHandler httpResourceHandler;// hasDefault
	private boolean voidMethodDirectReturn;// hasDefault:false
	private HttpHandlerFactory httpHandlerFactory;// hasDefault
	private TrafficForecastFactory trafficForecastFactory;// hasDefault
	private LogicExecutionStatistics logicExecutionStatistics;// hasDefault
	/**
	 * The default maximum allowable size for a single incoming thrift request
	 * or outgoing thrift response. A server can configure the actual maximum to
	 * be much higher (up to 0x7FFFFFFF or almost 2 GB). This default could also
	 * be safely bumped up, but 64MB is chosen simply because it seems
	 * reasonable that if you are sending requests or responses larger than
	 * that, it should be a conscious decision (something you must manually
	 * configure).
	 */
	private static final int MAX_FRAME_SIZE = 64 * 1024 * 1024;

	/**
	 * Create a ThriftServerDefBuilder with common defaults
	 */
	public ThriftServerDefBuilderBase() {
	}

	/**
	 * Give the endpoint a more meaningful name.
	 */
	@SuppressWarnings("unchecked")
	public T name(String name) {
		this.name = name;
		return (T) this;
	}

	/**
	 * Listen to this port.
	 */
	@SuppressWarnings("unchecked")
	public T listen(int serverPort) {
		this.serverPort = serverPort;
		return (T) this;
	}

	/**
	 * Specify the TProcessor.
	 */
	@SuppressWarnings("unchecked")
	public T withNettyProcessorFactory(final NettyProcessorFactory nettyProcessorFactory) {
		this.nettyProcessorFactory = nettyProcessorFactory;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T withProcessor(@SuppressWarnings("rawtypes") TBaseProcessor processor) {
		this.processor = processor;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T protocolFactorySelectorFactory(ProtocolFactorySelectorFactory protocolFactorySelectorFactory) {
		this.protocolFactorySelectorFactory = protocolFactorySelectorFactory;
		return (T) this;
	}

	/**
	 * Set frame size limit. Default is MAX_FRAME_SIZE
	 */
	@SuppressWarnings("unchecked")
	public T limitFrameSizeTo(int maxFrameSize) {
		this.maxFrameSize = maxFrameSize;
		return (T) this;
	}

	/**
	 * Set maximum number of connections. Default is 0 (unlimited)
	 */
	@SuppressWarnings("unchecked")
	public T limitConnectionsTo(int maxConnections) {
		this.maxConnections = maxConnections;
		return (T) this;
	}

	/**
	 * Limit number of queued responses per connection, before pausing reads to
	 * catch up.
	 */
	@SuppressWarnings("unchecked")
	public T limitQueuedResponsesPerConnection(int queuedResponseLimit) {
		this.queuedResponseLimit = queuedResponseLimit;
		return (T) this;
	}

	/**
	 * Specify timeout during which if connected client doesn't send a message,
	 * server will disconnect the client
	 */
	@SuppressWarnings("unchecked")
	public T clientIdleTimeout(long clientIdleTimeout) {
		this.clientIdleTimeout = clientIdleTimeout;
		return (T) this;
	}

	/**
	 * Specify an executor for thrift processor invocations ( i.e. = THaHsServer
	 * ) By default invocation happens in Netty single thread ( i.e. =
	 * TNonBlockingServer )
	 */
	@SuppressWarnings("unchecked")
	public T using(ExecutorService exe) {
		this.executor = exe;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T contextHandlerInstaller(ChannelHandler contextHandlerInstaller) {
		this.contextHandlerInstaller = contextHandlerInstaller;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T httpResourceHandler(HttpResourceHandler httpResourceHandler) {
		this.httpResourceHandler = httpResourceHandler;
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public T voidMethodDirectReturn(boolean voidMethodDirectReturn) {
		this.voidMethodDirectReturn = voidMethodDirectReturn;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T httpHandlerFactory(HttpHandlerFactory httpHandlerFactory) {
		this.httpHandlerFactory = httpHandlerFactory;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T trafficForecastFactory(TrafficForecastFactory trafficForecast) {
		this.trafficForecastFactory = trafficForecast;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T logicExecutionStatistics(LogicExecutionStatistics logicExecutionStatistics) {
		this.logicExecutionStatistics = logicExecutionStatistics;
		return (T) this;
	}

	/**
	 * Build the ThriftServerDef
	 */
	public ThriftServerDef build() {
		checkState(processor != null, "Processor not defined!");

		checkState(maxConnections >= 0, "maxConnections should be 0 (for unlimited) or positive");
		if (executor == null) {
			executor = new DefaultExecutorServiceFactory("NettyThrift")
					.newExecutorService(Runtime.getRuntime().availableProcessors() + 1);
		}
		if (protocolFactorySelectorFactory == null) {
			protocolFactorySelectorFactory = new DefaultProtocolFactorySelectorFactory();
		}
		if (httpResourceHandler == null) {
			httpResourceHandler = new HttpFileResourceHandler();
		}
		return new ThriftServerDef(name, serverPort, maxFrameSize, maxConnections, queuedResponseLimit,
				nettyProcessorFactory, contextHandlerInstaller, processor, executor, clientIdleTimeout,
				protocolFactorySelectorFactory, httpResourceHandler, voidMethodDirectReturn, httpHandlerFactory,
				trafficForecastFactory, logicExecutionStatistics);
	}

	/**
	 * Ensures the truth of an expression involving the state of the calling
	 * instance, but not involving any parameters to the calling method.
	 *
	 * @param expression
	 *            a boolean expression
	 * @throws IllegalStateException
	 *             if {@code expression} is false
	 */
	public static void checkState(boolean expression) {
		if (!expression) {
			throw new IllegalStateException();
		}
	}

	/**
	 * Ensures the truth of an expression involving the state of the calling
	 * instance, but not involving any parameters to the calling method.
	 *
	 * @param expression
	 *            a boolean expression
	 * @param errorMessage
	 *            the exception message to use if the check fails; will be
	 *            converted to a string using {@link String#valueOf(Object)}
	 * @throws IllegalStateException
	 *             if {@code expression} is false
	 */
	public static void checkState(boolean expression, Object errorMessage) {
		if (!expression) {
			throw new IllegalStateException(String.valueOf(errorMessage));
		}
	}
}