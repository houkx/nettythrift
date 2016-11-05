/**
 * 
 */
package io.nettythrift.protocol;

/**
 * @author HouKx
 *
 */
public interface ProtocolFactorySelectorFactory {

	ProtocolFactorySelector createProtocolFactorySelector(Class<?> ifaceClass);
}
