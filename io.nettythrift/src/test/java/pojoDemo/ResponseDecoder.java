/**
 * 
 */
package pojoDemo;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * @author HouKangxi
 *
 */
public class ResponseDecoder extends ByteToMessageDecoder {

	public ResponseDecoder() {
		super();
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		if(!in.isReadable()){
			return;
		}
		Response t = new Response();
		t.setId(in.readInt());
		t.setTime(in.readLong());
		out.add(t);
	}

}
