package alab.thriftLabService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSocket;
import org.junit.Assert;
import org.junit.Test;

import com.google.gson.Gson;

import alab.thriftLabService.TBankService.AsyncClient.getBalance_call;
import io.nettythrift.protocol.SimpleJSONProtocol;

public class BankClient_Test {
	static class BankReq {
		TUser user;
		TGetBalanceRequest req;

		public BankReq(TUser user, TGetBalanceRequest req) {
			this.user = user;
			this.req = req;
		}

	}

	@Test
	public void test_getBalance_simpleJsonArrayStruct_http() throws Exception {
		HttpPost post = new HttpPost("http://localhost:8080");
		Object[] args = new Object[] { myUser(), getReq() };
		StringBuilder sb = new StringBuilder();
		sb.append("[\"getBalance\",1,1,").append(new Gson().toJson(args)).append(']');
		String jsonStr = sb.toString();
		System.out.println("JsonStr = " + jsonStr);
		byte[] jsonBytes = jsonStr.getBytes("UTF-8");
		byte[] b;
		b = jsonBytes;
		// final int LEN = jsonBytes.length;
		// b = new byte[LEN + 4];
		// b[0] = (byte)(LEN >> 24);
		// b[1] = (byte)((LEN<<8) >> 24);
		// b[2] = (byte)((LEN<<16) >> 24);
		// b[3] = (byte)((LEN<<24) >> 24);
		// System.arraycopy(jsonBytes, 0, b, 4, jsonBytes.length);
		ContentType contentType = ContentType.create("text/json", "UTF-8");
		ByteArrayEntity entity = new ByteArrayEntity(b, contentType);
		// StringEntity entity = new StringEntity(sb.toString(), "UTF-8");
		post.setEntity(entity);
		HttpClient client = HttpClientBuilder.create().build();
		org.apache.http.HttpResponse response = client.execute(post, (HttpContext) null);
		int code = response.getStatusLine().getStatusCode();
		System.out.println("code = " + code);
		if (code == 200) {
			/** 读取服务器返回过来的json字符串数据 **/
			String strResult = EntityUtils.toString(response.getEntity(), "UTF-8");
			System.out.println("resp:\n" + strResult);
		}
	}

	@Test
	public void test_getBalance_simpleJson_http() throws Exception {
		HttpPost post = new HttpPost("http://localhost:8080");
		BankReq bean = new BankReq(getUser(), getReq());
		StringBuilder sb = new StringBuilder();
		sb.append("[\"getBalance\",1,1,").append(new Gson().toJson(bean)).append(']');
		String jsonStr = sb.toString();
		byte[] jsonBytes = jsonStr.getBytes("UTF-8");
		byte[] b;
		b = jsonBytes;
		// final int LEN = jsonBytes.length;
		// b = new byte[LEN + 4];
		// b[0] = (byte)(LEN >> 24);
		// b[1] = (byte)((LEN<<8) >> 24);
		// b[2] = (byte)((LEN<<16) >> 24);
		// b[3] = (byte)((LEN<<24) >> 24);
		// System.arraycopy(jsonBytes, 0, b, 4, jsonBytes.length);
		ContentType contentType = ContentType.create("text/json", "UTF-8");
		ByteArrayEntity entity = new ByteArrayEntity(b, contentType);
		// StringEntity entity = new StringEntity(sb.toString(), "UTF-8");
		post.setEntity(entity);
		HttpClient client = HttpClientBuilder.create().build();
		org.apache.http.HttpResponse response = client.execute(post, (HttpContext) null);
		int code = response.getStatusLine().getStatusCode();
		System.out.println("code = " + code);
		if (code == 200) {
			/** 读取服务器返回过来的json字符串数据 **/
			String strResult = EntityUtils.toString(response.getEntity(), "UTF-8");
			System.out.println("resp:\n" + strResult);
		}
	}

	@Test
	public void test_getBalance_simpleJson_async() throws Exception {
		TNonblockingTransport transport = new TNonblockingSocket("localhost", 8080);
		// # Wrap in a protocol
		// @SuppressWarnings("serial")
		// TProtocolFactory protocolFactory = new TProtocolFactory() {
		// @Override
		// public TProtocol getProtocol(TTransport trans) {
		// return new SimpleJSONProtocol(trans, TBankService.Iface.class);
		// }
		// };
		TProtocolFactory protocolFactory = new TCompactProtocol.Factory();
		// //TODO
		// # Create a client to use the protocol encoder
		TAsyncClientManager clientManager = new TAsyncClientManager();
		TBankService.AsyncClient client = new TBankService.AsyncClient(protocolFactory, clientManager, transport);
		//
		TUser user = getUser();

		// user.write(oprot);
		TGetBalanceRequest req = getReq();
		//
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean success = new AtomicBoolean();
		client.getBalance(user, req, new AsyncMethodCallback<TBankService.AsyncClient.getBalance_call>() {

			@Override
			public void onError(Exception exception) {
				exception.printStackTrace();
				latch.countDown();
			}

			@Override
			public void onComplete(getBalance_call response) {
				try {
					System.out.println(response.getResult());
					success.set(true);
				} catch (TException e) {
					e.printStackTrace();
				} finally {
					latch.countDown();
				}
			}
		});
		latch.await();
		Assert.assertTrue(success.get());
	}

	@Test
	public void test_getBalance_simpleJson() throws Exception {
		TSocket transport = new TSocket("localhost", 8080);
		// # Wrap in a protocol
		SimpleJSONProtocol protocol = new SimpleJSONProtocol(transport);
		// //TODO
		protocol.setArgsTBaseClass(TBankService.getBalance_result.class);
		// # Create a client to use the protocol encoder
		TBankService.Client client = new TBankService.Client(protocol);
		// # Connect!
		transport.open();
		//
		TUser user = getUser();

		// user.write(oprot);
		TGetBalanceRequest req = getReq();
		StringBuilder city = new StringBuilder();
		//
		for (int i = 0; i < 100; i++) {
			city.append(UUID.randomUUID().toString());
		}
		System.out.println("城市size = "+city.length());
		req.setCity(city.toString());

		Object resp = client.getBalance(user, req);
		System.out.println(resp);
		client.getInputProtocol().getTransport().close();
	}

	private TGetBalanceRequest getReq() {
		TGetBalanceRequest req = new TGetBalanceRequest();
		req.setCardNumber(System.nanoTime());
		req.setCity("BeiJIng");
		return req;
	}

	static class MyUser {
		public int age; // required
		public String name; // required
		public double length; // required
		public List<Integer> supportTypes; // required
		public Map<String, String> otherDescs; // required
		public long uid; // required

		public void setUid(long uid) {
			this.uid = uid;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setAge(int age) {
			this.age = age;
		}

		public void setLength(double length) {
			this.length = length;
		}

		public void setSupportTypes(List<Integer> supportTypes) {
			this.supportTypes = supportTypes;
		}

		public void setOtherDescs(Map<String, String> otherDescs) {
			this.otherDescs = otherDescs;
		}

	}

	private MyUser myUser() {
		MyUser user = new MyUser();
		user.setAge(18);
		user.setLength(1.68);
		user.setUid(System.currentTimeMillis());
		user.setName("慧子");
		user.setSupportTypes(Arrays.asList(1, 6, 8, 13, 14));
		Map<String, String> otherDescs = new HashMap<String, String>();
		otherDescs.put("city", "北京");
		otherDescs.put("country", "ZhongGuo");
		user.setOtherDescs(otherDescs);
		return user;
	}

	private TUser getUser() {
		TUser user = new TUser();
		user.setAge(18);
		user.setLength(1.68);
		user.setUid(System.currentTimeMillis());
		user.setName("慧子");
		user.setSupportTypes(Arrays.asList(1, 6, 8, 13, 14));
		Map<String, String> otherDescs = new HashMap<String, String>();
		otherDescs.put("city", "北京");
		otherDescs.put("country", "ZhongGuo");
		user.setOtherDescs(otherDescs);
		return user;
	}

	@Test
	public void test_getBalance_json() throws Exception {
		TSocket transport = new TSocket("localhost", 8080);
		TProtocol protocol = new TJSONProtocol(transport);
		// # Wrap in a protocol
		// TProtocolFactory factory = new TSimpleJSONProtocol.Factory();
		// TSimpleJSONProtocol protocol = (TSimpleJSONProtocol)
		// factory.getProtocol(transport);
		// //TODO
		// protocol.setArgsTBaseClass(TBankService.getBalance_result.class);
		// # Create a client to use the protocol encoder
		TBankService.Client client = new TBankService.Client(protocol);
		// # Connect!
		transport.open();
		TUser user = getUser();

		TGetBalanceRequest req = getReq();
		//
		Object resp = client.getBalance(user, req);
		System.out.println(resp);
	}
}
