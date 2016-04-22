package traditional.async;

import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TNonblockingServer.Args;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;

import aLab_commonDefines.CalculatorService;
import aLab_commonDefines.CalculatorServiceImpl;

public class CalcServer_async {

	public static void main(String[] _args) throws Exception {
		try {
			TNonblockingServerSocket serverTransport = new TNonblockingServerSocket(9090);
			CalculatorService.Iface iface = new CalculatorServiceImpl();
			TProcessor processor = new CalculatorService.Processor<CalculatorService.Iface>(iface);
			TProtocolFactory factory = new TCompactProtocol.Factory();
			Args args = new Args(serverTransport).processor(processor).protocolFactory(factory);
			// TServer server = new TSimpleServer(new
			// Args(serverTransport).processor(processor).protocolFactory(factory));
			TServer server = new TNonblockingServer(args);
			// Use this for a multithreaded server
			// TServer server = new TThreadPoolServer(new
			// TThreadPoolServer.Args(serverTransport).processor(processor));

			System.out.println("Starting the async server at prot: 9090");
			server.serve();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
