/**
 * 
 */
package io.nettythrift.core;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * 空闲关闭的handler
 * 
 * @author HouKx
 *
 */
public class IdleDisconnectHandler extends IdleStateHandler {

	public IdleDisconnectHandler(long allIdleTime, TimeUnit unit) {
		super(0, 0, allIdleTime, unit);
	}

	public IdleDisconnectHandler(long readerIdleTime, long writerIdleTime, long allIdleTime, TimeUnit unit) {
		super(readerIdleTime, writerIdleTime, allIdleTime, unit);
	}

	@Override
	protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
//		System.out.println("关闭空闲连接: " + ctx.channel());
		ctx.channel().close();
	}
}
