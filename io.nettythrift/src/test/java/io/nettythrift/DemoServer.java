package io.nettythrift;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.TBaseProcessor;
import org.apache.thrift.TException;

import io.nettythrift.bootstrap.ServerBootstrap;
import io.nettythrift.core.ThriftServerDef;

public class DemoServer {

	public static void main(String[] args) throws Exception {
		int port = 8083;

		TBaseProcessor<?> processor = new TCalculator.Processor<TCalculator.Iface>(new CalcIfaceImpl());

		ThriftServerDef serverDef = ThriftServerDef.newBuilder().listen(port)//
				.withProcessor(processor)//
				.using(Executors.newCachedThreadPool())//
				.clientIdleTimeout(TimeUnit.SECONDS.toMillis(15)).build();
		final ServerBootstrap server = new ServerBootstrap(serverDef);
		// 启动 Server
		server.start();
	}

	private static class CalcIfaceImpl implements TCalculator.Iface {

		@Override
		public String ping() throws TException {
			System.out.println("***　ping()...");
			return "PONG";
		}

		@Override
		public int add(int num1, int num2) throws TException {
			System.out.printf("***　add:(%d, %d)\n", num1, num2);
			return num1 + num2;
		}

		@Override
		public void zip(String str, int type) throws TException {
			System.out.printf("***　zip:(%s, %d)\n", str, type);
		}

		@Override
		public void uploadAction(String str) throws TException {
			System.out.printf("***　uploadAction:(%s)\n", str);
		}
	}
}
