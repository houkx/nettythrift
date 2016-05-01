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
package io.nettythrift.transport;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;

/**
 * Wraps incoming channel buffer into TTransport and provides a output buffer.
 */
public class TNiftyTransport extends TTransport {
	private final Channel channel;
	private final ByteBuf in;
	private ByteBuf out;
	private static final int DEFAULT_OUTPUT_BUFFER_SIZE = 1024;
	private final int initialReaderIndex;
	private final int initialBufferPosition;
	private int bufferPosition;
	private int bufferEnd;
	private final byte[] buffer;

	public static final int MOD_R = 1;
	public static final int MOD_W = 2;
	public static final int MOD_RW = 4;

	public TNiftyTransport(Channel channel, ByteBuf in, int mod) {
		this.channel = channel;
		this.in = in;
		// this.out = ByteBufs.dynamicBuffer(DEFAULT_OUTPUT_BUFFER_SIZE);
//		if ((mod & MOD_W) == MOD_W) {
			this.out = PooledByteBufAllocator.DEFAULT.directBuffer(DEFAULT_OUTPUT_BUFFER_SIZE);
//		}

		this.initialReaderIndex = in != null ? in.readerIndex() : 0;
		if (in != null) {
			if (!in.hasArray()) {
				buffer = null;
				bufferPosition = 0;
				initialBufferPosition = bufferEnd = -1;
			} else {
				buffer = in.array();
				initialBufferPosition = bufferPosition = in.arrayOffset() + in.readerIndex();
				bufferEnd = bufferPosition + in.readableBytes();
				// Without this, reading from a !in.hasArray() buffer will
				// advance the readerIndex
				// of the buffer, while reading from a in.hasArray() buffer will
				// not advance the
				// readerIndex, and this has led to subtle bugs. This should
				// help to identify
				// those problems by making things more consistent.
				in.readerIndex(in.readerIndex() + in.readableBytes());
			}
		} else {
			buffer = null;
			initialBufferPosition = bufferPosition = bufferEnd = 0;
		}
	}

	@Override
	public boolean isOpen() {
		return channel.isOpen();
	}

	@Override
	public void open() throws TTransportException {
		// no-op
	}

	@Override
	public void close() {
		// no-op
		channel.close();
	}

	@Override
	public int read(byte[] bytes, int offset, int length) throws TTransportException {

		if (getBytesRemainingInBuffer() >= 0) {
			int _read = Math.min(getBytesRemainingInBuffer(), length);
			System.arraycopy(getBuffer(), getBufferPosition(), bytes, offset, _read);
			consumeBuffer(_read);
			// System.out.printf(getClass().getSimpleName() +
			// "::read[Remaining]: offset=%d, length=%d, bytes=%s\n",
			// offset, _read, Arrays.toString(bytes));
			return _read;
		} else {
			int _read = Math.min(in.readableBytes(), length);
			// System.out.printf("curReaderIndex=%d, readable? %s, size = %d,
			// _read = %d, length =
			// %d\n",in.readerIndex(),in.isReadable(),in.readableBytes(),
			// _read,length);
			in.readBytes(bytes, offset, _read);
			// System.out.printf(getClass().getSimpleName() + "::read:
			// offset=%d, length=%d, bytes=%s\n", offset, _read,
			// Arrays.toString(bytes));
			return _read;
		}

	}

	@Override
	public int readAll(byte[] bytes, int offset, int length) throws TTransportException {
		if (read(bytes, offset, length) < length) {
			throw new TTransportException("Buffer doesn't have enough bytes to read");
		}
		return length;
	}

	@Override
	public void write(byte[] bytes, int offset, int length) throws TTransportException {
		out.writeBytes(bytes, offset, length);
	}

	public ByteBuf getOutputBuffer() {
		return out;
	}

	public void setOutputBuffer(ByteBuf buf) {
		out = buf;
	}

	@Override
	public void flush() throws TTransportException {
		// Flush is a no-op: NiftyDispatcher will write the response to the
		// Channel, in order to
		// guarantee ordering of responses when required.
	}

	@Override
	public void consumeBuffer(int len) {
		bufferPosition += len;
	}

	@Override
	public byte[] getBuffer() {
		return buffer;
	}

	@Override
	public int getBufferPosition() {
		return bufferPosition;
	}

	@Override
	public int getBytesRemainingInBuffer() {
		return bufferEnd - bufferPosition;
	}

	public int getReadByteCount() {
		if (getBytesRemainingInBuffer() >= 0) {
			return getBufferPosition() - initialBufferPosition;
		} else {
			return in.readerIndex() - initialReaderIndex;
		}
	}

	public int getWrittenByteCount() {
		return getOutputBuffer().writerIndex();
	}
}
