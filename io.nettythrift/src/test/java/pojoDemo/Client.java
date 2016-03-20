package pojoDemo;

import java.util.Random;

import echo.netty.EchoClient_netty;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.nettythrift.CommonClient;

public class Client extends CommonClient {
	public static void main(String[] args) throws Exception {
		String host;
		int port;
		final int defaultPort = 8080;
		if (args.length < 1) {
			host = "127.0.0.1";
			port = defaultPort;
			System.err.println(
					"Usage: " + EchoClient_netty.class.getSimpleName() + " <port>, use default port:" + defaultPort);
		} else {
			host = args[0];
			int m = host.indexOf(':');
			if (m > 0) {
				port = Integer.parseInt(host.substring(m + 1).trim());
			} else {
				port = defaultPort;
			}
		}
		new Client().start(host, port, new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new RequestEncoder(),new  ResponseDecoder(), new ClientHandler());
			}
		});
	}

	@Override
	protected void doOnChannelFuture(ChannelFuture f) {
		Request firstReq = new Request();
		firstReq.setId(new Random().nextInt(100));
		f.channel().write(firstReq);
		 f.channel().flush();
	}
}
