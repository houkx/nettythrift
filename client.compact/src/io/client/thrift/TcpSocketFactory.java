/**
 * 
 */
package io.client.thrift;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;

/**
 * @author HouKangxi
 *
 */
public class TcpSocketFactory extends SocketFactory {
	protected String host;
	protected int port;
	private int soTimeOut = 2000;
	private SSLContext sslContext;

	public TcpSocketFactory(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public TcpSocketFactory(String host, int port, SSLContext sslContext) {
		this.host = host;
		this.port = port;
		this.sslContext = sslContext;
	}

	public TcpSocketFactory setSoTimeOut(int soTimeOut) {
		this.soTimeOut = soTimeOut;
		return this;
	}

	@Override
	public int hashCode() {
		return host.hashCode() + port;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.net.SocketFactory#createSocket(java.lang.String, int)
	 */
	@Override
	public Socket createSocket() throws IOException {
		Socket socket;
		if (sslContext == null) {
			socket = new Socket(host, port);
		} else {
			socket = sslContext.getSocketFactory().createSocket(host, port);
		}
		if (soTimeOut > 0) {
			socket.setSoTimeout(soTimeOut);
		}
		return socket;
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return createSocket();
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return null;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
			throws IOException, UnknownHostException {
		return null;
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
			throws IOException {
		return null;
	}

}
