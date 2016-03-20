/**
 * 
 */
package io.nettythrift;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.thrift.TBaseProcessor;

/**
 * 服务的基本配置
 * 
 * @author HouKangxi
 *
 */
@SuppressWarnings("rawtypes")
public final class ServerConfig {
	private int port = 9090;
	private NioProcessor processor;
	private long taskTimeoutMillis;
	private int maxFrameLength = Integer.MAX_VALUE;
   private ProxyHandler proxyHandler;
   
	public <I> ServerConfig(TBaseProcessor<I> processor) {
		this.processor = new NioProcessor<I>(processor, Executors.newCachedThreadPool());
	}

	public <I> ServerConfig(TBaseProcessor<I> processor, ExecutorService executor) {
		this.processor = new NioProcessor<I>(processor, executor);
	}

	public <I> ServerConfig(NioProcessor<I> processor) {
		this.processor = processor;
	}

	public int getPort() {
		return port;
	}

	public ServerConfig setPort(int port) {
		this.port = port;
		return this;
	}

	public NioProcessor getProcessor() {
		return processor;
	}

	public ServerConfig setProcessor(NioProcessor processor) {
		this.processor = processor;
		return this;
	}

	public long getTaskTimeoutMillis() {
		return taskTimeoutMillis;
	}

	public ServerConfig setTaskTimeoutMillis(long taskTimeoutMillis) {
		this.taskTimeoutMillis = taskTimeoutMillis;
		return this;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}

	public ServerConfig setMaxFrameLength(int maxFrameLength) {
		this.maxFrameLength = maxFrameLength;
		return this;
	}

	public ProxyHandler getProxyHandler() {
		return proxyHandler;
	}

	public ServerConfig setProxyHandler(ProxyHandler proxyHandler) {
		this.proxyHandler = proxyHandler;
		return this;
	}

}
