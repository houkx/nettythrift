/**
 * 
 */
package echo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;


/**
 * @author HouKangxi
 *
 */
public class EchoClient_javaNio {

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		new EchoClient_javaNio().request(8080);
	}
	public void request(int port) throws IOException {
		final AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel
				.open();
		InetSocketAddress address = new InetSocketAddress("127.0.0.1",port);
		final CountDownLatch latch = new CountDownLatch(1);
		socketChannel.connect(address,"uid123",new CompletionHandler<Void, Object>() {

			@Override
			public void completed(Void result, Object attachment) {
				String newData = "New String to write to file..." + System.currentTimeMillis();

				final ByteBuffer buffer = ByteBuffer.allocate(48);
				buffer.clear();
				buffer.put(newData.getBytes());

				buffer.flip();
				socketChannel.write(buffer, attachment, new CompletionHandler<Integer, Object>() {
					@Override
					public void completed(Integer result, Object attachment) {
						socketChannel.read(buffer, attachment,
								new CompletionHandler<Integer, Object>() {

									@Override
									public void completed(Integer result,
											Object attachment) {
											try {
												buffer.flip();
												String strResp = new String(buffer.array(),buffer.position(),buffer.limit());
												System.out
														.printf("response: result=%s,attachment=%s,strRes=%s,byteBuffer=%s\n",
																result,
																attachment,
																strResp,
																buffer);
											} finally {
												latch.countDown();
											}
									}

									@Override
									public void failed(Throwable exc,
											Object attachment) {
										latch.countDown();	
									}
								}); // #8
					}

					@Override
					public void failed(Throwable exc, Object attachment) {
						
					}
				}); // #7
				
			}

			@Override
			public void failed(Throwable exc, Object attachment) {
				exc.printStackTrace();
				latch.countDown();				
			}
		});
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
}
