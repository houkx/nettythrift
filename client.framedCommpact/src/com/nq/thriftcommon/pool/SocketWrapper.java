/**
 * 
 */
package com.nq.thriftcommon.pool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * 套接字包装类，close()操作归还连接给连接池
 * 
 * @author HouKangxi
 *
 */
class SocketWrapper extends Socket {
	final Socket target;
	final SocketConnectionPool pool;

	final long createTime;
	volatile long lastUseTime;
	volatile boolean isWorking;

	// SocketWrapper() {
	// createTime = System.currentTimeMillis();
	// }

	SocketWrapper(Socket target, SocketConnectionPool pool) {
		this.target = target;
		this.pool = pool;
		createTime = System.currentTimeMillis();
	}

	/**
	 * 关闭操作，不做关闭，而是归还到连接池
	 */
	@Override
	public void close() throws IOException {
		if (pool != null)
			pool.returnToPool(this);
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return target.getOutputStream();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return target.getInputStream();
	}
//
//	@Override
//	public InetAddress getInetAddress() {
//		return target.getInetAddress();
//	}
//
//	@Override
//	public InetAddress getLocalAddress() {
//		return target.getLocalAddress();
//	}
//
//	@Override
//	public int getPort() {
//		return target.getPort();
//	}
//
//	@Override
//	public int getLocalPort() {
//		return target.getLocalPort();
//	}
//
//	@Override
//	public SocketAddress getRemoteSocketAddress() {
//		return target.getRemoteSocketAddress();
//	}
//
//	@Override
//	public SocketAddress getLocalSocketAddress() {
//		return target.getLocalSocketAddress();
//	}
//
//	@Override
//	public SocketChannel getChannel() {
//		return target.getChannel();
//	}
//
//	@Override
//	public boolean getOOBInline() throws SocketException {
//		return target.getOOBInline();
//	}
//
//	@Override
//	public synchronized int getReceiveBufferSize() throws SocketException {
//		return target.getReceiveBufferSize();
//	}
//
//	@Override
//	public boolean getKeepAlive() throws SocketException {
//
//		return target.getKeepAlive();
//	}
//
//	@Override
//	public boolean getReuseAddress() throws SocketException {
//
//		return target.getReuseAddress();
//	}

//	@Override
//	public boolean isConnected() {
//
//		return target.isConnected();
//	}
//
//	@Override
//	public boolean isBound() {
//
//		return target.isBound();
//	}
//
//	@Override
//	public boolean isClosed() {
//
//		return target.isClosed();
//	}

//	@Override
//	public boolean isInputShutdown() {
//
//		return target.isInputShutdown();
//	}
//
//	@Override
//	public boolean isOutputShutdown() {
//		return target.isOutputShutdown();
//	}
//
//	@Override
//	public String toString() {
//		return target.toString() + " [createTime=" + createTime + ", lastUseTime=" + lastUseTime + ", isWorking="
//				+ isWorking + "]";
//	}

}
