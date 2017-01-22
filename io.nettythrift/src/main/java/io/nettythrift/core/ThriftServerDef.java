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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseProcessor;

import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.SocketChannel;
import io.nettythrift.protocol.ProtocolFactorySelector;
import io.nettythrift.protocol.ProtocolFactorySelectorFactory;

/**
 * Descriptor for a Thrift Server. This defines a listener port that Server need
 * to start a Thrift endpoint.
 */
public class ThriftServerDef {
	// === CONSTANTS ======

	// === configurations ===
	public final String name;
	public final int serverPort;
	public final int maxFrameSize;
	public final int maxConnections;
	public final int queuedResponseLimit;
	public final NettyProcessor nettyProcessor;
	public final ChannelHandler codecInstaller;
	@SuppressWarnings("rawtypes")
	public final Map<String, ProcessFunction<?, ? extends TBase>> processMap;
	public final Object iface;
	public final ExecutorService executor;
	public final long clientIdleTimeout;
	public final ProtocolFactorySelector protocolFactorySelector;
	public final HttpResourceHandler httpResourceHandler;
	public final boolean voidMethodDirectReturn;
	public final HttpHandlerFactory httpHandlerFactory;
	private final int[] voidMethodHashes;
	public final TrafficForecast trafficForecast;
	public final LogicExecutionStatistics logicExecutionStatistics;

	@SuppressWarnings("unchecked")
	public ThriftServerDef(String name, int serverPort, int maxFrameSize, int maxConnections, int queuedResponseLimit,
			NettyProcessorFactory nettyProcessorFactory, ChannelHandler codecInstaller,
			@SuppressWarnings("rawtypes") TBaseProcessor processor, ExecutorService executor, long clientIdleTimeout,
			ProtocolFactorySelectorFactory protocolFactorySelectorFactory, HttpResourceHandler httpResourceHandler,
			boolean voidMethodDirectReturn, HttpHandlerFactory httpHandlerFactory, TrafficForecastFactory trafficForecastFac,
			LogicExecutionStatistics _logicExecutionStatistics) {
		super();
		this.name = name;
		this.serverPort = serverPort;
		this.maxFrameSize = maxFrameSize;
		this.maxConnections = maxConnections;
		this.queuedResponseLimit = queuedResponseLimit;
		this.processMap = processor.getProcessMapView();
		this.executor = executor;
		this.clientIdleTimeout = clientIdleTimeout;
		this.httpResourceHandler = httpResourceHandler;
		this.voidMethodDirectReturn = voidMethodDirectReturn;
		this.httpHandlerFactory = httpHandlerFactory == null ? new DefaultHttpHandlerFactory() : httpHandlerFactory;
		if (nettyProcessorFactory == null) {
			nettyProcessorFactory = new DefaultNettyProcessorFactory();
		}
		if (codecInstaller == null) {
			codecInstaller = new DefaultChannelInitializer<SocketChannel>(this);
		}
		this.nettyProcessor = nettyProcessorFactory.create(this);
		this.codecInstaller = codecInstaller;
		Object iface = null;
		try {
			Field f = TBaseProcessor.class.getDeclaredField("iface");
			f.setAccessible(true);
			iface = f.get(processor);
		} catch (Exception e) {
			e.printStackTrace();
		}
		Class<?> ifaceClass = null;
		{
			Class<?> clazz = iface.getClass();
			boolean find = false;
			while (!find && clazz != null) {
				Class<?>[] ifcs = clazz.getInterfaces();
				if (ifcs != null && ifcs.length > 0) {
					for (Class<?> c : ifcs) {
						if (c.getEnclosingClass() != null && c.getSimpleName().equals("Iface")) {
							ifaceClass = c;
							find = true;
							break;
						}
					}
				}
				clazz = clazz.getSuperclass();
			}
		}
		Map<String, Integer> inits = Collections.emptyMap();
		int[] voidMethodHashes = null;
		if (ifaceClass != null) {
			inits = new HashMap<>();
			Method[] ms = ifaceClass.getMethods();
			int len = 0;
			int[] hashes = new int[ms.length];
			for (Method m : ms) {
				if (m.getReturnType() == void.class) {
					hashes[len++] = m.getName().hashCode();
					inits.put(m.getName(), 128);
				} else {
					inits.put(m.getName(), 1024);
				}
			}
			if (len > 0) {
				if (len < ms.length) {
					voidMethodHashes = new int[len];
					System.arraycopy(hashes, 0, voidMethodHashes, 0, len);
				} else {
					voidMethodHashes = hashes;
				}
				Arrays.sort(voidMethodHashes);
			}
		}
		this.trafficForecast = trafficForecastFac != null ? trafficForecastFac.create(inits)
				: new DefaultTrafficForecastImpl(inits, Integer.parseInt(System.getProperty("trafficForecast.logmax", "100")));
		this.logicExecutionStatistics = _logicExecutionStatistics != null ? _logicExecutionStatistics
				: new DefaultLogicExecutionStatisticsImpl(Integer.parseInt(System.getProperty("ioexe.threshold", "5")),
						Integer.parseInt(System.getProperty("ioexe.logmax", "100")));
		this.voidMethodHashes = voidMethodHashes;
		this.iface = iface;
		protocolFactorySelector = protocolFactorySelectorFactory.createProtocolFactorySelector(ifaceClass);
	}

	public boolean isVoidMethod(String methodName) {
		// 目前thrift不支持方法重载，所以可以用方法名唯一确定一个方法
		if (voidMethodHashes != null && methodName != null) {
			return Arrays.binarySearch(voidMethodHashes, methodName.hashCode()) >= 0;
		}
		return false;
	}

	public static ThriftServerDefBuilder newBuilder() {
		return new ThriftServerDefBuilder();
	}

}
