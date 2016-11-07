/**
 * 
 */
package io.client.thrift;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import io.client.thrift.Json.Strategy;
import io.client.thrift.annotaion.Index;

/**
 * 使用 HTTP + JSON 协议
 * <p>
 * 只有这一个文件，适合轻量级通信，如单一接口。由于依赖的 org.json 包 在 android.jar中 已存在，代码整体尺寸很小。
 * 
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
	 * 调用者可以实现自定义的 SocketFactory来内部配置Socket参数(如超时时间，SSL等),也可以通过返回包装的Socket来实现连接池<br/>
	 * SocketFactory::createSocket(String host,int ip)//NOTE: 实际传入createSocket(methodName,flag)
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
    
	@SuppressWarnings("unchecked")
	public static <INTERFACE> INTERFACE getClientInterface(Class<INTERFACE> ifaceClass, String host) {
		long part1 = ifaceClass.getName().hashCode();
		final Long KEY = (part1 << 32) | host.hashCode();
		INTERFACE iface = (INTERFACE) ifaceCache.get(KEY);
		if (iface == null) {
			iface = (INTERFACE) Proxy.newProxyInstance(ifaceClass.getClassLoader(), new Class[] { ifaceClass },
					new Handler(host));
			ifaceCache.putIfAbsent(KEY, iface);
		}
		return iface;
	}

	private static ConcurrentHashMap<Class<?>, Map<Object, Field>> fieldCache = new ConcurrentHashMap<Class<?>, Map<Object, Field>>();

	private static class Handler extends Json.Strategy implements InvocationHandler {
		final AtomicInteger seqIdHolder = new AtomicInteger(0);
		final SocketFactory factory;
		String host;

		Handler(SocketFactory factory) {
			this.factory = factory;
		}

		Handler(String host) {
			factory = null;
			this.host = host;
		}

		{ // 只处理public 且非 static 的字段
			publicFieldsOnly();
		}

		@Override
		public String fieldName(Field field) {
			Index id = field.getAnnotation(Index.class);
			if (id != null) {
				return String.valueOf(id.value());
			}
			return super.fieldName(field);
		}

		@Override
		public Field field(Class<?> cls, String fieldName) throws NoSuchFieldException, SecurityException {
			char c0 = fieldName.charAt(0);
			if (c0 >= '0' && c0 <= '9') {
				Map<Object, Field> cache = fieldCache.get(cls);
				if (cache == null) {
					Field[] fs = cls.getFields();
					cache = new HashMap<Object, Field>(fs.length);
					for (Field f : fs) {
						Index id = f.getAnnotation(Index.class);
						if (id != null) {
							cache.put(id.value(), f);
						}
					}
					fieldCache.putIfAbsent(cls, cache);
				}
				Field fd = cache.get(Integer.parseInt(fieldName));
				return fd;
			}
			return super.field(cls, fieldName);
		}

		@Override
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
			//

			StringBuilder sb = new StringBuilder(256);
			sb.append('[').append('"').append(methodName).append('"')//
					.append(',').append(1).append(',').append(seqId).append(',')
					.append(args == null ? "[]" : Json.toJson(args, this)).append(']');
			String jsonStr = sb.toString();
			//
			System.out.println("===========  Request ============");
			System.out.println(jsonStr);
			//

			Socket connection = null;
			Reader reader = null;
			try {
				InputStream in;
				OutputStream out;
				if (factory != null) {
					connection = factory.createSocket(methodName, 0);
					out = connection.getOutputStream();
					out.write(jsonStr.getBytes());
					out.flush();
					in = connection.getInputStream();
				} else {
					URLConnection conn = new URL(host).openConnection();
					conn.setDoOutput(true);
					conn.connect();
					out = conn.getOutputStream();
					out.write(jsonStr.getBytes());
					out.flush();
					in = conn.getInputStream();
				}
				int firstByte = in.read();
				reader = new InputStreamReader(in, "UTF-8");
				sb = new StringBuilder(512);
				if (firstByte == '[') {
					sb.append((char) firstByte);
				}
				char[] charbuf = new char[512];
				int len;
				while ((len = reader.read(charbuf)) > 0) {
					sb.append(charbuf, 0, len);
					if (len < charbuf.length) {
						break;
					}
				}
				jsonStr = sb.toString();
			} catch (IOException ex) {
				jsonStr = "";
			} finally {
				try {
					if (connection != null) {
						connection.close();
					} else if (reader != null) {
						reader.close();
					}
				} catch (IOException e) {
				}
			}
			System.out.println("===========  response json: ===========");
			System.out.println(jsonStr);
			if (jsonStr == null || jsonStr.length() < 1) {
				return null;
			}
			return read(jsonStr, method.getReturnType(), method.getExceptionTypes(), seqId, this);
		}

	}

	private static <T> T read(String json, Type resultBeanClass, Class<?>[] exceptionsTypes, int seqid_,
			Strategy strategy) throws Throwable {
		JSONArray arr = new JSONArray(json);
		String methodName = arr.getString(0);
		int type = arr.getInt(1);
		int seqid = arr.getInt(2);
		if (type == 3) {
			JSONObject respFull = arr.getJSONObject(arr.length() - 1);
			String errMsg = respFull.getString("message");
			throw new ProtocolException(errMsg);
		}
		// 验证seqId 与请求的是否相同
		if (seqid != seqid_) {
			throw new ProtocolException(methodName + " failed: out of sequence response");
		}
		JSONObject respFull = arr.getJSONObject(arr.length() - 1);
		Object respObj = respFull.opt("success");
		if (respObj == null) {
			respObj = respFull.opt("0");
		}
		if (respObj == null) {
			respObj = respFull.opt("ex");
			if (respObj == null) {
				respObj = respFull.opt("1");
			}
			if (respObj != null && exceptionsTypes != null && exceptionsTypes.length > 0) {
				resultBeanClass = exceptionsTypes[0];
				String objson = Json.toJson(resultBeanClass, respObj, strategy);
				Object ex = Json.fromJson(objson, resultBeanClass, strategy);
				if (ex instanceof Throwable) {
					throw (Throwable) ex;
				}
			}
			return null;
		} else {
			String objson = Json.toJson(resultBeanClass, respObj, strategy);
			T bean = Json.fromJson(objson, resultBeanClass, strategy);
			//
			return bean;
		}
	}
}
