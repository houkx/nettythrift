/**
 * 
 */
package calacServer;

import org.apache.thrift.TBaseProcessor;
import org.junit.Test;

import aLab_commonDefines.CalculatorService;
import aLab_commonDefines.CalculatorServiceImpl;
import io.nettythrift.ServerConfig;
import io.nettythrift.ThriftCommonServer;

/**
 * @author houkangxi
 *
 */
public class CalcServerBootstrap {
	static class G<T> {
		G() {
		}
	}

	@Test
	public void seeGenericClass() {
		TBaseProcessor<CalculatorService.Iface> processor = new CalculatorService.Processor<CalculatorService.Iface>(
				new CalculatorServiceImpl());
		ServerConfig serverDef = new ServerConfig(processor).setProxyHandler(new NqProxyHandler())
				.setTaskTimeoutMillis(8000);
		Class clazz = serverDef.getProcessor().getClass();
		java.lang.reflect.TypeVariable v = clazz.getTypeParameters()[0];
		System.out.println("v = " + v);
		System.out.println("gv = " + clazz.getGenericSuperclass());
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		TBaseProcessor<CalculatorService.Iface> processor = new CalculatorService.Processor<CalculatorService.Iface>(
				new CalculatorServiceImpl());
		new CalculatorService.ping_args();
		ServerConfig serverDef = new ServerConfig(processor).setProxyHandler(new NqProxyHandler())
				.setTaskTimeoutMillis(8000);

		new ThriftCommonServer(serverDef).start();
	}

}
