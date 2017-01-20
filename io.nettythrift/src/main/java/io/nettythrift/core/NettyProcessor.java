/**
 * 
 */
package io.nettythrift.core;

import org.apache.thrift.protocol.TProtocol;

import io.netty.channel.ChannelHandlerContext;

/**
 * @author HouKx
 *
 */
public interface NettyProcessor {

	void process(ChannelHandlerContext ctx, TProtocol in, TProtocol out, WriterHandler onComplete)
			throws Exception;
}
