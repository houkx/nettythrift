/**
 * 
 */
package io.client.thrift;

import java.io.ByteArrayOutputStream;
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
	 * <br/><pre>
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
			try {
				connection = factory.createSocket();
				OutputStream out = connection.getOutputStream();
				out.write(outbuff.toByteArray());
				out.flush();
				InputStream in = connection.getInputStream();
				if (in != null) {
					protocol.transIn = in;
					rs = ProtocolIOUtil.read(protocol, method.getGenericReturnType(), method.getExceptionTypes(),
							seqId);
				}
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
			return rs;
		}

	}

}
