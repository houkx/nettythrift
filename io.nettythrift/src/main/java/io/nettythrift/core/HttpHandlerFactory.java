/**
 * 
 */
package io.nettythrift.core;

import io.netty.channel.ChannelHandler;

/**
 * @author HouKx
 *
 */
public interface HttpHandlerFactory {

	ChannelHandler create(ThriftServerDef def);
}
