package aSimpleHtttp;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransportException;
import org.junit.Assert;
import org.junit.Test;

public class HttpProtocolTest {
	@Test
	public void testSimpleServlet() throws TTransportException, TException {
		// 在浏览器输入： http://localhost:9090?[1,%22ping%22,1,1,{}]
		// TODO : change this URL if it's not the right one ;o)
		String servletUrl = "http://localhost:9090";

		THttpClient thc = new THttpClient(servletUrl);
//		TProtocol loPFactory = new TJSONProtocol(thc);
		 TProtocol loPFactory = new TCompactProtocol(thc);
		// TProtocol loPFactory = new TBinaryProtocol(thc);
		// TProtocol loPFactory = new TSimpleJSONProtocol(thc);
		SimpleService.Client client = new SimpleService.Client(loPFactory);
		String resp = client.ping();
		System.out.println(resp);
		Assert.assertEquals("PONG", resp);
	}
}
