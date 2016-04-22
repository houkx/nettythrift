/**
 * 
 */
package aSimpleHtttp;

import org.apache.thrift.TException;

/**
 * @author HouKangxi
 *
 */
public class SimpleServiceImpl implements SimpleService.Iface{

	@Override
	public String ping() throws TException {
		System.out.println("Call: ping()");
		return "PONG";
	}

}
