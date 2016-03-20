/**
 * 
 */
package pojoDemo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @author houkangxi
 *
 */
public class RequestEncoder extends MessageToByteEncoder<Request> {

	@Override
	protected void encode(ChannelHandlerContext ctx, Request msg, ByteBuf out) throws Exception {
            out.writeInt(msg.getId());		
	}

}
