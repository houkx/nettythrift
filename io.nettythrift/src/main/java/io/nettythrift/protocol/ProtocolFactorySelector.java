/**
 * 
 */
package io.nettythrift.protocol;

import java.util.HashMap;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author HouKx
 *
 */
public class ProtocolFactorySelector {
	private static Logger logger = LoggerFactory.getLogger(ProtocolFactorySelector.class);
	private final HashMap<Short, TProtocolFactory> protocolFactoryMap = new HashMap<Short, TProtocolFactory>(8);

	public ProtocolFactorySelector() {
	}

	public ProtocolFactorySelector(@SuppressWarnings("rawtypes") Class interfaceClass) {
		protocolFactoryMap.put((short) -32767, new TBinaryProtocol.Factory());
		protocolFactoryMap.put((short) -32223, new TCompactProtocol.Factory());
		protocolFactoryMap.put((short) 23345, new TJSONProtocol.Factory());
		if (interfaceClass != null) {
			protocolFactoryMap.put((short) 23330, new TSimpleJSONProtocol.Factory(interfaceClass));
		}
	}

	protected void registProtocolFactory(short head, TProtocolFactory factory) {
		protocolFactoryMap.put(head, factory);
	}

	public TProtocolFactory getProtocolFactory(short head) {
		// SimpleJson的前两个字符为：[" ，而TJSONProtocol的第二个字符为一个数字
		TProtocolFactory fac = protocolFactoryMap.get(head);
		if (logger.isDebugEnabled()) {
			logger.debug("head:{}, getProtocolFactory:{}", head, fac);
		}
		return fac;
	}
}
