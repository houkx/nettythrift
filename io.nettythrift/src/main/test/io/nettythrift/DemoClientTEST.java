/**
 * 
 */
package io.nettythrift;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * @author HouKx
 *
 */
public class DemoClientTEST {
	// TSimpleJsonProtocol format
	private static final String PING = "[\"ping\",1,2,[]]";

	@Test
	public void test_tcp_shortMsg_framed() throws Exception {
		Socket socket = null;
		try {
			socket = new Socket("localhost", 8083);
			// configSocket(socket);
			/**
			 * 发送TCP请求
			 */
			OutputStream out = socket.getOutputStream();
			byte[] frame;
			{
				byte[] arrContent = PING.getBytes();
				final int msgLen = arrContent.length;
				// System.out.printf("*** 客户端 msgLen = %d, time=%d,
				// connection = %s\n", msgLen, System.currentTimeMillis(),
				// connection);
				frame = new byte[4 + msgLen];// 前四个字节代表消息长度
				frame[0] = (byte) (msgLen >> 24);
				frame[1] = (byte) ((msgLen >> 16) & 0xff);
				frame[2] = (byte) ((msgLen >> 8) & 0xff);
				frame[3] = (byte) (msgLen & 0xff);
				// System.out.printf("** arrayLen = [%d, %d, %d, %d]\n",
				// arr4Req[0], arr4Req[1], arr4Req[2], arr4Req[3]);
				System.arraycopy(arrContent, 0, frame, 4, msgLen);
			}
			out.write(frame);
			out.flush();
			System.out.println("==== 响应 ========");
			InputStream in = socket.getInputStream();
			int readLen = in.read(frame, 0, 4);
			if (readLen == 1) {
				readLen = in.read(frame, 1, 4);
			}
			System.out.println("readLen = " + readLen);
			System.out.println(new String(read(in)));
			in.close();
		} finally {
			System.out.println("关闭socket");
			if (socket != null) {
				socket.close();
			}
		}
	}

	@Test
	public void test_tcp_shortMsg() throws Exception {
		Socket socket = null;
		try {
			socket = new Socket("localhost", 8083);
			// configSocket(socket);
			/**
			 * 发送TCP请求
			 */
			OutputStream out = socket.getOutputStream();
			out.write(PING.getBytes());
			out.flush();
			System.out.println("==== 响应 ========");
			InputStream in = socket.getInputStream();
			System.out.println(new String(read(in)));
			in.close();
		} finally {
			System.out.println("关闭socket");
			if (socket != null) {
				socket.close();
			}
		}
	}

	@Test
	public void test_tcp_shortMsgAndProxy() throws Exception {
		Socket socket = new Socket();
		try {
			// configSocket(socket);
			// socket.connect(new InetSocketAddress("10.50.0.174", 8081), 6000);
			socket.connect(new InetSocketAddress("localhost", 8083), 6000);
			socket.setReuseAddress(true);
			socket.setSoLinger(false, 0);
			socket.setTcpNoDelay(true);
			socket.setKeepAlive(false);
			// socket.setSoTimeout(5000);
			/**
			 * 发送TCP请求
			 */
			OutputStream out = socket.getOutputStream();
			String proxy = "PROXY TCP4 198.51.100.22 203.0.113.7 35646 80\r\n";
			// out.write((proxy + getPingBao).getBytes());
			out.write((proxy + PING).getBytes());
			out.flush();
			System.out.println("==== 响应 ========");
			System.out.println(new String(read(socket.getInputStream())));
		} finally {
			System.out.println("关闭socket");
			socket.close();
		}
	}

	String proxy = "PROXY TCP4 198.51.100.22 203.0.113.7 35646 80\r\n";

	@Test
	public void test_MockHttpProxyToNettyServer() throws Exception {
		Map<String, String> params = new HashMap<String, String>();
		params.put("", PING);
		Map<String, String> respMap = HTTPUtil.sendPostRequest("http://localhost:8083", params, "UTF-8", proxy);
		HTTPUtil.printResult(respMap);
	}

	@Test
	public void test_MockHttpToNettyServer() throws Exception {
		Map<String, String> params = new HashMap<String, String>();
		params.put("", PING);
		Map<String, String> respMap = HTTPUtil.sendPostRequest("http://localhost:8083", params, "UTF-8");
		HTTPUtil.printResult(respMap);
	}

	private static byte[] read(InputStream in) throws IOException {
		// 事实上就像JDK的API所述:Closing a ByteArrayOutputStream has no effect
		// 查询ByteArrayOutputStream.close()的源码会发现,它没有做任何事情,所以其close()与否是无所谓的
		ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		byte[] buffer = new byte[512];
		int len = -1;

		while ((len = in.read(buffer)) > 0) {
			System.out.println("readBuf: len=" + len);
			// 将读取到的字节写到ByteArrayOutputStream中
			// 所以最终ByteArrayOutputStream的字节数应该等于HTTP响应报文的整体长度,而大于HTTP响应正文的长度
			bytesOut.write(buffer, 0, len);
			if (len < buffer.length) {
				break;
			}
		}
		// 响应的原始字节数组
		return bytesOut.toByteArray();
	}

}
