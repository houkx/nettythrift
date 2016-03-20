package traditional.sync;

import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TSimpleJSONProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

import aLab_commonDefines.CalculatorService;
import aLab_commonDefines.CalculatorServiceImpl;

public class CalcServer {

	public static void main(String[] args) throws Exception{
	    try {
	        TServerTransport serverTransport = new TServerSocket(9090);
	        TProcessor processor = new CalculatorService.Processor<CalculatorService.Iface>(new CalculatorServiceImpl());
			TProtocolFactory factory = new TSimpleJSONProtocol.Factory();
			TServer server = new TSimpleServer(new Args(serverTransport).processor(processor)
					.protocolFactory(factory ));

	        // Use this for a multithreaded server
	        // TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));

	        System.out.println("Starting the simple server at prot: 9090");
	        server.serve();
	      } catch (Exception e) {
	        e.printStackTrace();
	      }
	}

}
