/**
 * 
 */
package io.nettythrift.core;

import java.io.IOException;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * @author HouKangxi
 *
 */
public interface HttpResourceHandler {

	public void process(ChannelHandlerContext ctx, FullHttpRequest msg, String uri,int dotPos) throws IOException;
}
