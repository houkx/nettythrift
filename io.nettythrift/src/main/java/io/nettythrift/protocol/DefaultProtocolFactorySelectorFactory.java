/**
 * 
 */
package io.nettythrift.protocol;

/**
 * @author HouKx
 *
 */
public class DefaultProtocolFactorySelectorFactory implements ProtocolFactorySelectorFactory {

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.netty5thrift.protocol.ProtocolFactorySelectorFactory#
	 * createProtocolFactorySelector(java.lang.Class)
	 */
	@Override
	public ProtocolFactorySelector createProtocolFactorySelector(Class<?> interfaceClass) {
		return new ProtocolFactorySelector(interfaceClass);
	}

}
