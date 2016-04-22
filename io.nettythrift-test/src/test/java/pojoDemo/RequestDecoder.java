/**
 * 
 */
package pojoDemo;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * @author houkangxi
 *
 */
public class RequestDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		if(!in.isReadable()){
			return;
		}
		Request req = new Request();
		req.setId(in.readInt());
		out.add(req);
	}

}
