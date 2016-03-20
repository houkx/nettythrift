/**
 * 
 */
package echo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author HouKangxi
 *
 */
public class PlainNioEchoServer {
	public static void main(String[] args) throws Exception {
		new PlainNioEchoServer().serve(8080);
	}
	public void serve(int port) throws IOException {
		System.out.println("Listening for connections on port " + port);
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		ServerSocket ss = serverChannel.socket();
		InetSocketAddress address = new InetSocketAddress(port);
		ss.bind(address); // #1 绑定端口
		serverChannel.configureBlocking(false);
		Selector selector = Selector.open();
		// 把Selector注册给Channel
		serverChannel.register(selector, SelectionKey.OP_ACCEPT); // #2
		while (true) {
			try {
				selector.select(); // #3 阻塞，一直到有东西被选中
			} catch (IOException ex) {
				ex.printStackTrace();
				// handle in a proper way
				break;
			}
			Set<SelectionKey> readyKeys = selector.selectedKeys(); // #4
			Iterator<SelectionKey> iterator = readyKeys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove(); // #5
				try {
					if (key.isAcceptable()) {
						ServerSocketChannel server = (ServerSocketChannel) key
								.channel();
						// 接收客户端连接
						SocketChannel client = server.accept(); // #6 Accept the client connection
						System.out
								.println("Accepted connection from " + client);
						client.configureBlocking(false);//配置为非阻塞模式
						//Registers this channel with the given selector, returning a selection key. 
						// Register connection to selector and set ByteBuffer
						client.register(selector, SelectionKey.OP_WRITE
								| SelectionKey.OP_READ,
								ByteBuffer.allocate(100)); // //#7
					}
					if (key.isReadable()) { // //#8
						SocketChannel client = (SocketChannel) key.channel();
						ByteBuffer output = (ByteBuffer) key.attachment();
						client.read(output); // //#9
					}
					if (key.isWritable()) { // //#10
						SocketChannel client = (SocketChannel) key.channel();
						ByteBuffer output = (ByteBuffer) key.attachment();
						output.flip();
						client.write(output); // #11
						output.compact();
					}
				} catch (IOException ex) {
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException cex) {
					}
				}
			}
		}
	}
}
