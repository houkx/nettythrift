/**
 * 
 */
package io.nettythrift;

import org.apache.thrift.TBase;

import io.netty.buffer.ByteBuf;

/**
 * @author houkangxi
 *
 */
public interface ProxyHandler {

    String getHeadProxyInfo(ByteBuf in);

	void handlerProxyInfo(@SuppressWarnings("rawtypes") TBase arg, String proxy);
}
