/**
 * 
 */
package io.nettythrift;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSocket;
import org.junit.Test;

import io.nettythrift.protocol.TSimpleJSONProtocol;
import junit.framework.Assert;

/**
 * @author HouKx
 *
 */
public class DemoClientTraditionalTEST {
	private static final String HOST = "localhost";
	private static final int PORT = 8083;

	@Test
	public void test_TCompactProtocol() throws Exception {
		test(new TCompactProtocol.Factory());
	}

	@Test
	public void test_TBinaryProtocol() throws Exception {
		test(new TBinaryProtocol.Factory());
	}

	@Test
	public void test_TJSONProtocol() throws Exception {
		test(new TJSONProtocol.Factory());
	}

	@Test
	public void test_TSimpleJSONProtocol() throws Exception {
		test(new TSimpleJSONProtocol.Factory(TCalculator.Iface.class, false));
	}

	@Test
	public void test_AsyncClient() throws Throwable {
		Random rnd = new Random(System.nanoTime());

		TProtocolFactory[] protfacs = new TProtocolFactory[] { new TCompactProtocol.Factory(),
				new TBinaryProtocol.Factory(), new TJSONProtocol.Factory(),
				new TSimpleJSONProtocol.Factory(TCalculator.Iface.class, false) };

		TProtocolFactory protocolFactory = protfacs[rnd.nextInt(protfacs.length)];

		System.out.println("protocolFactory: " + protocolFactory);

		TAsyncClientManager clientManager = new TAsyncClientManager();
		TNonblockingTransport transport = new TNonblockingSocket(HOST, PORT);
		TCalculator.AsyncClient client = new TCalculator.AsyncClient(protocolFactory, clientManager, transport);
		final int num1 = rnd.nextInt(Integer.MAX_VALUE / 2 - 1);
		final int num2 = rnd.nextInt(Integer.MAX_VALUE / 2 - 1);

		final CountDownLatch latch = new CountDownLatch(1);
		final Throwable[] exceptions = new Throwable[1];
		AsyncMethodCallback<TCalculator.AsyncClient.add_call> resultHandler = new AsyncMethodCallback<TCalculator.AsyncClient.add_call>() {
			@Override
			public void onComplete(TCalculator.AsyncClient.add_call response) {
				System.out.println("onComplete!");
				try {
					int result = response.getResult();
					Assert.assertEquals(num1 + num2, result);
				} catch (Throwable e) {
					exceptions[0] = e;
				} finally {
					latch.countDown();
				}
			}

			@Override
			public void onError(Exception exception) {
				System.err.println("onError!");
				exception.printStackTrace();
				latch.countDown();
			}

		};
		client.add(num1, num2, resultHandler);
		latch.await();
		transport.close();
		if (exceptions[0] != null) {
			throw exceptions[0];
		}
	}

	void test(TProtocolFactory fac) throws Exception {
		TProtocol prot = fac.getProtocol(socket());
		TCalculator.Client client = null;
		try {
			client = new TCalculator.Client(prot);
			org.junit.Assert.assertEquals(2, client.add(1, 1));
		} finally {
			if (client != null) {
				client.getInputProtocol().getTransport().close();
				client.getOutputProtocol().getTransport().close();
			}
		}
	}

	TSocket socket() throws Exception {
		TSocket sc = new TSocket(HOST, PORT);
		sc.open();
		return sc;
	}
}
