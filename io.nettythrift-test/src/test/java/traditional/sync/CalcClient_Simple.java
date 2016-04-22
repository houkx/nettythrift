package traditional.sync;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.junit.Assert;
import org.junit.Test;

import aLab_commonDefines.CalculatorService;
import io.nettythrift.protocol.SimpleJSONProtocol;

public class CalcClient_Simple {
	static final int B = 0x5B;
	@Test
	public void testSimple_class4name() throws Exception {
		System.out.println(0x5B00);
		System.out.println(0x8200);
		System.out.println(0x8000);
		System.out.println(8*16*16*16);
//		Class.forName("aLab_commonDefines.CalculatorService$ping_args");
	}
	@Test
	public void testSimple_ping() throws Exception {
		Assert.assertEquals("PONG", test_ping());
	}

	@Test
	public void testSimple_add() throws Exception {
		Assert.assertEquals(17, test_add(2, 15));
	}

	public static void main(String[] args) throws Exception {
		ExecutorService ex = Executors.newCachedThreadPool();
		int sum = 10;
		final CountDownLatch latch = new CountDownLatch(sum);
		while (sum-- > 0) {
			ex.submit(new Runnable() {
				public void run() {
					try {
						Random r = new Random(System.nanoTime());
						 test_add(r.nextInt(1000),r.nextInt(90000));
						// test_add(2,15);
//						test_ping();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				}
			});
		}
		latch.await();
		ex.shutdown();
		ex.awaitTermination(1, TimeUnit.SECONDS);
	}

	private static String test_ping() throws TTransportException, TException {
		TSocket transport = new TSocket("localhost", 9090);
		// # Wrap in a protocol
		TProtocolFactory factory = new SimpleJSONProtocol.Factory();
		SimpleJSONProtocol protocol = (SimpleJSONProtocol) factory.getProtocol(transport);
		protocol.setArgsTBaseClass(CalculatorService.ping_result.class); 
		// # Create a client to use the protocol encoder
		CalculatorService.Client client = new CalculatorService.Client(protocol);
		// # Connect!
		transport.open();
		String resp = client.ping();
		System.out.println(resp);
		return resp;
	}

	private static int test_add(int n1, int n2) throws TTransportException, TException {
		TSocket transport = new TSocket("localhost", 9090);
		// # Wrap in a protocol
//		 TProtocolFactory factory = new TCompactProtocol.Factory();
		TProtocolFactory factory = new SimpleJSONProtocol.Factory();
		SimpleJSONProtocol protocol = (SimpleJSONProtocol) factory.getProtocol(transport);
		protocol.setArgsTBaseClass(CalculatorService.add_result.class); 
		// # Create a client to use the protocol encoder
		CalculatorService.Client client = new CalculatorService.Client(protocol);
		// # Connect!
		transport.open();
		// client.ping();
		int resp = client.add(n1, n2);
		Assert.assertEquals(n1+n2, resp);
		System.out.printf("%d + %d = %d\n", n1, n2, resp);
		return resp;
	}

}
