package alab.thriftLabService;

import org.apache.thrift.TBaseProcessor;

import calacServer.NqProxyHandler;
import io.nettythrift.ServerConfig;
import io.nettythrift.ThriftCommonServer;

public class BankServiceBootstrap {
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		TBaseProcessor<TBankService.Iface> processor = new TBankService.Processor<TBankService.Iface>(new BankServiceImpl());
		new TBankService.getBalance_args();
		ServerConfig serverDef = new ServerConfig(processor)
				.setProxyHandler(new NqProxyHandler())
				.setTaskTimeoutMillis(9000).setPort(8080);

		new ThriftCommonServer(serverDef).start();
	}
}
