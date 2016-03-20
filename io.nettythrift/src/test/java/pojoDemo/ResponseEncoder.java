/**
 * 
 */
package pojoDemo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @author HouKangxi
 *
 */
public class ResponseEncoder extends MessageToByteEncoder<Response> {

	public ResponseEncoder() {
		super();
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, Response msg, ByteBuf out) throws Exception {
		out.writeInt(msg.getId());
		out.writeLong(msg.getTime());
	}

}
