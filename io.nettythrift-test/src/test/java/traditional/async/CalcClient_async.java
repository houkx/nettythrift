package traditional.async;

import java.util.concurrent.CountDownLatch;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;

import aLab_commonDefines.CalculatorService;
import aLab_commonDefines.CalculatorService.AsyncClient.ping_call;

public class CalcClient_async {
	static final byte B1 = (byte)0x80;
	//0x 80  01 00 01
	 static final int VERSION_1 = 0x80010000|1;
	public static void main(String[] args) throws Exception {
		TNonblockingTransport transport = new TNonblockingSocket("localhost", 9090);
		// # Wrap in a protocol
//		TProtocolFactory protocolFactory = new org.apache.thrift.protocol.TCompactProtocol.Factory();
		TProtocolFactory protocolFactory = new org.apache.thrift.protocol.TBinaryProtocol.Factory();
//		TProtocolFactory protocolFactory=new TProtocolFactory(){
//			@Override
//			public TProtocol getProtocol(TTransport trans) {
//				return new SimpleJSONProtocol(trans, CalculatorService.Iface.class);
//			}
//		};
//		TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
//		TProtocolFactory protocolFactory = new TJSONProtocol.Factory();
		TAsyncClientManager clientManager = new TAsyncClientManager();
		// # Create a client to use the protocol encoder
		CalculatorService.AsyncClient client = new CalculatorService.AsyncClient(protocolFactory,
				clientManager, transport);
		// invoke
		final CountDownLatch latch = new CountDownLatch(1);
//		 test_ping(client, latch);
		test_add(client, latch);
		latch.await();
	}

	@SuppressWarnings("unused")
	private static void test_ping(CalculatorService.AsyncClient client,
			final CountDownLatch latch) throws TException {
		client.ping(new AsyncMethodCallback<CalculatorService.AsyncClient.ping_call>() {
			@Override
			public void onError(Exception exception) {
				exception.printStackTrace();
				latch.countDown();
			}

			@Override
			public void onComplete(ping_call response) {
				try {
					response.getResult();
					System.out.println("client: getResult");
				} catch (TException e) {
					e.printStackTrace();
				} finally {
					latch.countDown();
				}
			}
		});
	}

	private static void test_add(CalculatorService.AsyncClient client, final CountDownLatch latch)
			throws TException {
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

}
