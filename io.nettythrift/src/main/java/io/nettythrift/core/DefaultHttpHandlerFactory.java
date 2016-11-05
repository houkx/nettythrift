/**
 * 
 */
package io.nettythrift.core;

import io.netty.channel.ChannelHandler;
import io.nettythrift.codec.HttpThriftBufDecoder;

/**
 * @author HouKx
 *
 */
public class DefaultHttpHandlerFactory implements HttpHandlerFactory {

	/* (non-Javadoc)
	 * @see io.netty5thrift.core.HttpHandlerFactory#create(io.netty5thrift.core.ThriftServerDef)
	 */
	@Override
	public ChannelHandler create(ThriftServerDef serverDef) {
		return new HttpThriftBufDecoder(serverDef);
	}

}
