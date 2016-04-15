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
package io.nettythrift;

import org.apache.thrift.protocol.TProtocolFactory;

import io.netty.buffer.ByteBuf;
import io.nettythrift.NioProcessor.ReadResult;
import io.nettythrift.transport.ThriftTransportType;

public class ThriftMessage {
	private final ByteBuf buffer;
	private final ThriftTransportType transportType;
	private long processStartTimeMillis;
	private TProtocolFactory proctocolFactory;// new added -- 用于动态协议
	public int responseCode;
	public String responseMessage;
	public String proxyInfo;
	public boolean hasRead;
	public ReadResult readResult;
	public boolean fromProgram;
	public boolean connectionKeepAlive;
	public ThriftMessage(ByteBuf buffer, ThriftTransportType transportType) {
		this.buffer = buffer;
		this.transportType = transportType;
	}

	public ByteBuf getBuffer() {
		return buffer;
	}

	public ThriftTransportType getTransportType() {
		return transportType;
	}

	public ThriftMessage clone(ByteBuf messageBuffer) {
		return new ThriftMessage(messageBuffer, transportType).setProctocolFactory(proctocolFactory)
				.setProcessStartTimeMillis(processStartTimeMillis).fromProgram(fromProgram)
				.setConnectionKeepAlive(connectionKeepAlive);
	}

	private ThriftMessage fromProgram(boolean fromProgram) {
		this.fromProgram = fromProgram;
		return this;
	}
	/**
	 * Standard Thrift clients require ordered responses, so even though Nifty
	 * can run multiple requests from the same client at the same time, the
	 * responses have to be held until all previous responses are ready and have
	 * been written. However, through the use of extended protocols and codecs,
	 * a request can indicate that the client understands out-of-order
	 * responses.
	 *
	 * @return {@code true} if ordered responses are required
	 */
	public boolean isOrderedResponsesRequired() {
		return true;
	}

	public long getProcessStartTimeMillis() {
		return processStartTimeMillis;
	}

	public ThriftMessage setProcessStartTimeMillis(long processStartTimeMillis) {
		this.processStartTimeMillis = processStartTimeMillis;
		return this;
	}

	public ThriftMessage setConnectionKeepAlive(boolean connectionKeepAlive) {
		this.connectionKeepAlive = connectionKeepAlive;
		return this;
	}

	public TProtocolFactory getProctocolFactory() {
		return proctocolFactory;
	}

	public ThriftMessage setProctocolFactory(TProtocolFactory proctocolFactory) {
		this.proctocolFactory = proctocolFactory;
		return this;
	}

	public String getProxyInfo() {
		return proxyInfo;
	}

	public ThriftMessage setProxyInfo(String proxyInfo) {
		this.proxyInfo = proxyInfo;
		return this;
	}

	@Override
	public String toString() {
		return "ThriftMessage@" + hashCode() + " [transportType=" + transportType + ", proctocolFactory="
				+ proctocolFactory + "]";
	}

}
