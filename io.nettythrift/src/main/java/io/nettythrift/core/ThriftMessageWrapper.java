/**
 *
 */
package io.nettythrift.core;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author HouKx
 */
public abstract class ThriftMessageWrapper {

    private ThriftMessageWrapper successor;

    public ThriftMessageWrapper() {
    }

    public ThriftMessageWrapper(ThriftMessageWrapper successor) {
        this.successor = successor;
    }

    public ThriftMessageWrapper getSuccessor() {
        return successor;
    }

    public void setSuccessor(ThriftMessageWrapper successor) {
        this.successor = successor;
    }

    public void beforeMessageWrite(ChannelHandlerContext ctx, ThriftMessage msg) {
    }

    public Object wrapMessage(ChannelHandlerContext ctx, ThriftMessage msg) {
        Object resp = wrapMessageInner(ctx, msg);
        if (successor != null) {
            resp = successor.wrapMessage(ctx, msg);
        }
        return resp;
    }

    protected Object wrapMessageInner(ChannelHandlerContext ctx, ThriftMessage msg) {
        return msg.getContent();
    }

    public void writeMessage(ChannelHandlerContext ctx, Object wrappedMsg) {
        if (successor == null) {
            writeMessageInner(ctx, wrappedMsg);
        } else {
            successor.writeMessage(ctx, wrappedMsg);
        }
    }

    protected void writeMessageInner(ChannelHandlerContext ctx, Object wrappedMsg) {
        ctx.writeAndFlush(wrappedMsg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }
}
