import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.junit.Assert;
import org.junit.Test;

import aLab_commonDefines.CalculatorService;
import aLab_commonDefines.CalculatorService.AsyncClient.ping_call;
import aSimpleHtttp.SimpleService;

/**
 * 
 */

/**
 * @author nq
 *
 */
public class TestAll {
	@Test
	public void test_http_ping() throws TTransportException, TException {
		// 在浏览器输入： http://localhost:9090?[1,%22ping%22,1,1,{}]
		// TODO : change this URL if it's not the right one ;o)
		String servletUrl = "http://localhost:9090";

		THttpClient thc = new THttpClient(servletUrl);
//		TProtocol loPFactory = new TJSONProtocol(thc);
		 TProtocol loPFactory = new TCompactProtocol(thc);
		// TProtocol loPFactory = new TBinaryProtocol(thc);
		// TProtocol loPFactory = new TSimpleJSONProtocol(thc);
		SimpleService.Client client = new SimpleService.Client(loPFactory);
		String resp = client.ping();
		System.out.println(resp);
		Assert.assertEquals("PONG", resp);
	}
	@Test
	public void testAsync_ping() throws Exception {
		TNonblockingTransport transport = new TNonblockingSocket("localhost", 9090);
		// # Wrap in a protocol
		// TProtocolFactory protocolFactory = new TCompactProtocol.Factory();
		TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
		// TProtocolFactory protocolFactory = new TJSONProtocol.Factory();
		TAsyncClientManager clientManager = new TAsyncClientManager();
		// # Create a client to use the protocol encoder
		CalculatorService.AsyncClient client = new CalculatorService.AsyncClient(protocolFactory, clientManager,
				transport);
		// invoke
		final CountDownLatch latch = new CountDownLatch(1);
		test_ping(client, latch);
		latch.await();
	}
 
	private static void test_ping(CalculatorService.AsyncClient client, final CountDownLatch latch) throws TException {
		client.ping(new AsyncMethodCallback<CalculatorService.AsyncClient.ping_call>() {
			@Override
			public void onError(Exception exception) {
				exception.printStackTrace();
				latch.countDown();
			}

			@Override
			public void onComplete(ping_call response) {
				try {
					String resp = response.getResult();
					System.out.println(resp);
					Assert.assertEquals("PONG", resp);
				} catch (TException e) {
					e.printStackTrace();
				} finally {
					latch.countDown();
				}
			}
		});
	}

	private static void test_add(CalculatorService.AsyncClient client, final CountDownLatch latch) throws TException {
		client.add(2, 15, new AsyncMethodCallback<CalculatorService.AsyncClient.add_call>() {
			@Override
			public void onError(Exception exception) {
				exception.printStackTrace();
				latch.countDown();
			}

			@Override
			public void onComplete(CalculatorService.AsyncClient.add_call response) {
				try {
					int rs = response.getResult();
					System.out.println("client: getResult = " + rs);
				} catch (TException e) {
					e.printStackTrace();
				} finally {
					latch.countDown();
				}
			}
		});
	}

	@Test
	public void test_Sync_ping() throws Exception {
		Assert.assertEquals("PONG", testSync_ping());
	}
	@Test
	public void test_Sync_add_TimeOut() throws Exception {
		try {
			testSync_add(1, 3);
		} catch (TException e) {
			Assert.assertEquals("ServerInternalTimeOut", e.getMessage());
		}
	}
	@Test
	public void test_Sync_add_exception() throws Exception {
		try {
			testSync_add(-1, 15);
		} catch (TException e) {
			Assert.assertEquals("Internal error processing add", e.getMessage());
		}
	}
	@Test
	public void test_Sync_add() throws Exception {
		Assert.assertEquals(17, testSync_add(2, 15));
	}

	private static String testSync_ping() throws TTransportException, TException {
		TSocket transport = new TSocket("localhost", 9090);
		// # Wrap in a protocol
		TProtocolFactory factory = new TJSONProtocol.Factory();
		TProtocol protocol = factory.getProtocol(transport);
		// # Create a client to use the protocol encoder
		CalculatorService.Client client = new CalculatorService.Client(protocol);
		// # Connect!
		transport.open();
		String resp = client.ping();
		System.out.println(resp);
		return resp;
	}

	private static int testSync_add(int n1, int n2) throws TTransportException, TException {
		TSocket transport = new TSocket("localhost", 9090);
		// # Wrap in a protocol
		TProtocolFactory factory = new TCompactProtocol.Factory();
		TProtocol protocol = factory.getProtocol(transport);
		// # Create a client to use the protocol encoder
		CalculatorService.Client client = new CalculatorService.Client(protocol);
		// # Connect!
		transport.open();
		// client.ping();
		int resp = client.add(n1, n2);
		System.out.printf("%d + %d = %d\n", n1, n2, resp);
		return resp;
	}

}
