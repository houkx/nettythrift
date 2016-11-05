/**
 * 
 */
package io.nettythrift.core;

/**
 * @author HouKx
 *
 */
public interface NettyProcessorFactory {
	NettyProcessor create(ThriftServerDef serverDef);
}
