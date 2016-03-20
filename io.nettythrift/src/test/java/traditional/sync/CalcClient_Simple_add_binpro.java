package traditional.sync;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

import aLab_commonDefines.CalculatorService;

public class CalcClient_Simple_add_binpro {

	public static void main(String[] args) throws Exception {
		ExecutorService ex = Executors.newCachedThreadPool();
		int sum = 1;
		final CountDownLatch latch = new CountDownLatch(sum);
		while(sum-->0){
			ex.submit(new Runnable() {
				public void run() {
					try {
						Random r = new Random(System.nanoTime());
						test_add(r.nextInt(1000),r.nextInt(90000));
//						test_ping();
					} catch (Exception e) {
						e.printStackTrace();
					}finally{
						latch.countDown();
					}
				}
			});
		}
		latch.await();
		ex.shutdown();
		ex.awaitTermination(1, TimeUnit.SECONDS);
	}
	private static void test_ping() throws TTransportException, TException {
		TSocket transport = new TSocket("localhost", 9090);
		// # Wrap in a protocol
		TProtocol protocol = new TCompactProtocol(transport);
		// # Create a client to use the protocol encoder
		CalculatorService.Client client = new CalculatorService.Client(protocol);
		// # Connect!
		transport.open();
		client.ping();
	}
	private static void test_add(int n1, int n2) throws TTransportException, TException {
		TSocket transport = new TSocket("localhost", 9090);
		// # Wrap in a protocol
		TProtocol protocol = new TCompactProtocol(transport);
		// # Create a client to use the protocol encoder
		CalculatorService.Client client = new CalculatorService.Client(protocol);
		// # Connect!
		transport.open();
//		client.ping();
		System.out.printf("%d + %d = %d\n",n1,n2,client.add(n1, n2));
	}

}
