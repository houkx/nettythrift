package echo.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

public class EchoClient_netty {
	public static void main(String[] args) throws Exception {
		String host;
		int port;
		final int defaultPort = 8080;
		if (args.length < 1) {
			host = "127.0.0.1";
			port = defaultPort;
			System.err.println("Usage: " + EchoClient_netty.class.getSimpleName()
					+ " <port>, use default port:" + defaultPort);
		} else {
			host = args[0];
			int m = host.indexOf(':');
			if (m > 0) {
				port = Integer.parseInt(host.substring(m + 1).trim());
			} else {
				port = defaultPort;
			}
		}
		new EchoClient_netty(host, port).start();
	}

	private final String host;
	private final int port;

	public EchoClient_netty(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void start() throws Exception {
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			Bootstrap b = new Bootstrap(); // #1
			b.group(group) // #2
					.channel(NioSocketChannel.class) // #3
					.remoteAddress(new InetSocketAddress(host, port)) // #4
					.handler(new ChannelInitializer<SocketChannel>() { // #5
								@Override
								public void initChannel(SocketChannel ch)
										throws Exception {
									ch.pipeline().addLast(
											new EchoClientHandler()); // #6
								}
							});
			ChannelFuture f = b.connect().sync(); // #7
			f.channel().closeFuture().sync(); // #8
		} finally {
			group.shutdownGracefully().sync(); // #9
		}
	}
}
