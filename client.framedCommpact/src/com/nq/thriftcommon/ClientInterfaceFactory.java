/**
 * 
 */
package com.nq.thriftcommon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

/**
 * @author HouKangxi
 *
 */
public class ClientInterfaceFactory {
	private ClientInterfaceFactory() {
	}

	private static ConcurrentHashMap<Long, Object> ifaceCache = new ConcurrentHashMap<Long, Object>();

	/**
	 * 获得与服务端通信的接口对象
	 * <p>
	 * 调用者可以实现自定义的 SocketFactory来内部配置Socket参数(如超时时间，SSL等),也可以通过返回包装的Socket来实现连接池
	 * <br/>
	 * 
	 * <pre>
	 *  class SocketPool extends javax.net.SocketFactory{
	 *    public Socket createSocket(String methodName,int flag)throws IOException{
	 *      return getSocketFromPool();
	 *    }
	 *    private MySocketWrapper getSocketFromPool(){
	 *     ...
	 *    }
	 *    private void returnToPool(MySocketWrapper socket){
	 *    ...
	 *    }
	 *  }
	 *  class MySocketWrapper extends Socket{
	 *    private Socket target;
	 *    private SocketPool pool;
	 *    // 包装close()方法，归还连接到连接池
	 *    public void close()throws IOException{
	 *      pool.returnToPool(this);
	 *    }
	 *    public OutputStream getOutputStream()throws IOException{
	 *        return target.getOutputStream();
	 *    }
	 *    public InputStream getInputStream()throws IOException{
	 *        return target.getInputStream();
	 *    }
	 *  }
	 * </pre>
	 * 
	 * <br/>
	 * 
	 * 
	 * @param ifaceClass
	 *            - 接口class
	 * @param factory
	 *            - 套接字工厂类, 注意：需要实现 createSocket() 方法，需要实现hashCode()方法来区分factory
	 * @return 接口对象
	 */
	@SuppressWarnings("unchecked")
	public static <INTERFACE> INTERFACE getClientInterface(Class<INTERFACE> ifaceClass, SocketFactory factory) {
		long part1 = ifaceClass.getName().hashCode();
		final Long KEY = (part1 << 32) | factory.hashCode();
		INTERFACE iface = (INTERFACE) ifaceCache.get(KEY);
		if (iface == null) {
			iface = (INTERFACE) Proxy.newProxyInstance(ifaceClass.getClassLoader(), new Class[] { ifaceClass },
					new Handler(factory));
			ifaceCache.putIfAbsent(KEY, iface);
		}
		return iface;
	}

	private static class Handler implements InvocationHandler {
		final AtomicInteger seqIdHolder = new AtomicInteger(0);
		final SocketFactory factory;

		public Handler(SocketFactory factory) {
			this.factory = factory;
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String methodName = method.getName();
			if (args == null || args.length == 0) {
				if (methodName.equals("toString")) {
					return Handler.class.getName() + "@" + System.identityHashCode(this);
				}
				if (methodName.equals("hashCode")) {
					return System.identityHashCode(this);
				}
			}

			int seqId = seqIdHolder.incrementAndGet();
			ByteArrayOutputStream outbuff = new ByteArrayOutputStream();
			TCompactProtocol protocol = new TCompactProtocol(outbuff, null);

			ProtocolIOUtil.write(methodName, seqId, protocol, method.getGenericParameterTypes(), args);
			Socket connection = null;
			Object rs = null;
			boolean success = true;
			try {
				byte[] arrContent = outbuff.toByteArray();
				int msgLen = arrContent.length;
				System.out.printf("*** 客户端 msgLen = %d, time=%d, connection = %s\n", msgLen, System.currentTimeMillis(),
						connection);
				byte[] arr4Req = new byte[4 + msgLen];// 代表着消息长度的四个字节
				arr4Req[0] = (byte) (msgLen >> 24);
				arr4Req[1] = (byte) ((msgLen >> 16) & 0xff);
				arr4Req[2] = (byte) ((msgLen >> 8) & 0xff);
				arr4Req[3] = (byte) (msgLen & 0xff);
				System.out.printf("** arrayLen = [%d, %d, %d, %d]\n", arr4Req[0], arr4Req[1], arr4Req[2], arr4Req[3]);
				System.arraycopy(arrContent, 0, arr4Req, 4, msgLen);

				connection = factory.createSocket();
				OutputStream out = connection.getOutputStream();
				out.write(arr4Req);
				out.flush();

				InputStream in = connection.getInputStream();
				if (in != null) {
					// int readLen = 0, offset = 0;
					// while (readLen < 4) {
					// readLen += in.read(arrLen, offset, 4 - readLen);
					// }
					int readLen = in.read(arr4Req, 0, 4);
					if (readLen == 1) {
						readLen = in.read(arr4Req, 1, 4);
						System.out.printf("** respArrayLen(!1) = [%d, %d, %d, %d]\n", arr4Req[1], arr4Req[2],
								arr4Req[3], arr4Req[4]);
					} else if (readLen == 4) {
						System.out.printf("** respArrayLen = [%d, %d, %d, %d]\n", arr4Req[0], arr4Req[1], arr4Req[2],
								arr4Req[3]);
					}
					System.out.println("readLen=" + readLen + ",connection = " + connection);

					if (readLen == 4) {
						// 此时arrLen代表返回结果的长度
						protocol.transIn = in;
						rs = ProtocolIOUtil.read(protocol, method.getGenericReturnType(), method.getExceptionTypes(),
								seqId);
					} else {
						System.out.println("arr[0]=" + arr4Req[0] + ", 出错的socket: " + connection);
					}
				}
			} catch (IOException ex) {
				success = false;
				throw ex;
			} catch (Throwable ex) {
				success = false;
				throw ex;
			} finally {
				if (connection != null) {
					if (success) {
						// 正常情况，通过socket.close()关闭，方便切换到定制业务
						connection.close();
					} else {
						// 异常情况，直接通过IO流关闭
						try {
							connection.getOutputStream().close();
							connection.getInputStream().close();
						} catch (Throwable e) {
						}
					}

				}
			}
			return rs;
		}

	}

}
