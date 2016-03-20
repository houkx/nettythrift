package echo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;

import org.apache.http.impl.execchain.MainClientExec;

public class PlainNio2EchoServer {
	public static void main(String[] args) throws Exception {
		new PlainNio2EchoServer().serve(8080);
	}

	public void serve(int port) throws IOException {
		System.out.println("Listening for connections on port " + port);
		final AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel
				.open();
		InetSocketAddress address = new InetSocketAddress(port);
		serverChannel.bind(address); // #1
		final CountDownLatch latch = new CountDownLatch(1);
		serverChannel.accept(null,
				new CompletionHandler<AsynchronousSocketChannel, Object>() { // #2
					@Override
					public void completed(
							final AsynchronousSocketChannel channel,
							Object attachment) {

						serverChannel.accept(null, this); // #3
						ByteBuffer buffer = ByteBuffer.allocate(100);
						channel.read(buffer, buffer, new EchoCompletionHandler(
								channel)); // #4
					}

					@Override
					public void failed(Throwable throwable, Object attachment) {
						try {
							serverChannel.close(); // #5
						} catch (IOException e) {
							// ingnore on close
						} finally {
							latch.countDown();
						}
					}
				});
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private final class EchoCompletionHandler implements
			CompletionHandler<Integer, ByteBuffer> {
		private final AsynchronousSocketChannel channel;

		EchoCompletionHandler(AsynchronousSocketChannel channel) {
			this.channel = channel;
		}

		@Override
		public void completed(Integer result, ByteBuffer buffer) {
			buffer.flip();
			channel.write(buffer, buffer,
					new CompletionHandler<Integer, ByteBuffer>() { // #6
						@Override
						public void completed(Integer result, ByteBuffer buffer) {
							if (buffer.hasRemaining()) {
								channel.write(buffer, buffer, this); // #7
							} else {
								buffer.compact();
								channel.read(buffer, buffer,
										EchoCompletionHandler.this); // #8
							}
						}

						@Override
						public void failed(Throwable exc, ByteBuffer attachment) {
							try {
								channel.close();
							} catch (IOException e) {
								// ingnore on close
							}
						}
					});
		}

		@Override
		public void failed(Throwable exc, ByteBuffer attachment) {
			exc.printStackTrace();
		}
	}
}