/**
 * 
 */
package io.nettythrift;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TSocket;
import org.junit.Test;

import io.nettythrift.protocol.TSimpleJSONProtocol;

/**
 * @author HouKx
 *
 */
public class DemoClientTraditionalTEST {

	@Test
	public void test_TCompactProtocol() throws Exception {
		test(new TCompactProtocol.Factory());
	}

	@Test
	public void test_TBinaryProtocol() throws Exception {
		test(new TBinaryProtocol.Factory());
	}

	@Test
	public void test_TJSONProtocol() throws Exception {
		test(new TJSONProtocol.Factory());
	}

	@Test
	public void test_TSimpleJSONProtocol() throws Exception {
		test(new TSimpleJSONProtocol.Factory(TCalculator.Iface.class, false));
	}

	void test(TProtocolFactory fac) throws Exception {
		TProtocol prot = fac.getProtocol(socket());
		TCalculator.Client client = null;
		try {
			client = new TCalculator.Client(prot);
			org.junit.Assert.assertEquals(2, client.add(1, 1));
		} finally {
			if (client != null) {
				client.getInputProtocol().getTransport().close();
				client.getOutputProtocol().getTransport().close();
			}
		}
	}

	TSocket socket() throws Exception {
		TSocket sc = new TSocket("localhost", 8083);
		sc.open();
		return sc;
	}
}
