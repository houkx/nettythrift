/**
 * 
 */
package calacServer;

import org.apache.thrift.TBaseProcessor;

import aLab_commonDefines.CalculatorService;
import aLab_commonDefines.CalculatorServiceImpl;
import io.nettythrift.ServerConfig;
import io.nettythrift.ThriftCommonServer;

/**
 * @author houkangxi
 *
 */
public class CalcServerBootstrap {
	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		TBaseProcessor<CalculatorService.Iface> processor = new CalculatorService.Processor<CalculatorService.Iface>(
				new CalculatorServiceImpl());
		
//		new CalculatorService.ping_args();
		
		ServerConfig serverDef = new ServerConfig(processor).setTaskTimeoutMillis(8000);

		new ThriftCommonServer(serverDef).start();
	}

}
