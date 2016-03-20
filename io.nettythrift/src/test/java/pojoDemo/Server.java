package pojoDemo;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.nettythrift.CommonServer;

public class Server extends ChannelInitializer<SocketChannel> {
	public static void main(String[] args) throws Exception {
		int port;
		if (args.length < 1) {
			port = 8080;
			System.err.println("Usage: " + Server.class.getSimpleName()
					+ " <port>, use default port:8080");
		} else {
			port = Integer.parseInt(args[0]);
		}
		new CommonServer().start(port, new Server());
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		ch.pipeline().addLast(
				new ResponseEncoder(),
				new RequestDecoder(), new ServerHandler()// 
				
				);
	}
}
