/**
 *
 */
package io.nettythrift.core;

import org.apache.thrift.protocol.TProtocolFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author HouKx
 */
public class ThriftMessage {

    private ByteBuf content;
    private TProtocolFactory protocolFactory;
    private ThriftMessageWrapper wrapper;

    public ThriftMessage() {
    }

    public ThriftMessage(ByteBuf content, TProtocolFactory protocolFactory) {
        this.content = content;
        this.protocolFactory = protocolFactory;
    }

    public ByteBuf getContent() {
        return content;
    }

    public ThriftMessage setContent(ByteBuf content) {
        this.content = content;
        return this;
    }

    public void write(ChannelHandlerContext ctx) {
        wrapper.writeMessage(ctx, wrapper.wrapMessage(ctx, this));
    }

    public ThriftMessage beforeWrite(ChannelHandlerContext ctx) {
        wrapper.beforeMessageWrite(ctx, this);
        return this;
    }

    public ThriftMessageWrapper getWrapper() {
        return wrapper;
    }

    public ThriftMessage setWrapper(ThriftMessageWrapper wrapper) {
        this.wrapper = wrapper;
        return this;
    }

    public TProtocolFactory getProtocolFactory() {
        return protocolFactory;
    }

    public ThriftMessage setProtocolFactory(TProtocolFactory protocolFactory) {
        this.protocolFactory = protocolFactory;
        return this;
    }

	@Override
	public String toString() {
		return "ThriftMessage [content=" + content + ", protocolFactory=" + protocolFactory + ", wrapper=" + wrapper
				+ "]";
	}

}
