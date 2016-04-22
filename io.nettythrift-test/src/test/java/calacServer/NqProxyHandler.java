/**
 * 
 */
package calacServer;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.thrift.TBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.nettythrift.ProxyHandler;

/**
 * @author houkangxi
 *
 */
public class NqProxyHandler implements ProxyHandler {
    private static Logger logger = LoggerFactory.getLogger(NqProxyHandler.class);
	private final static Pattern IP_PATTERN = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

	@Override
	public String getHeadProxyInfo(ByteBuf buffer) {
		// 判断和PROXY的前三个字符是否相同，相同则认为使用了代理，并读出代理信息
		if (buffer.readableBytes() > 3 && buffer.getByte(1) == 82 && buffer.getByte(2) == 79) {
			// 自定义的代理头标记
			byte read;
			byte[] aa = new byte[70];
			int c = 0;
			while ((read = buffer.readByte()) != (byte) '\n' && c < 70) {
				aa[c++] = read;
			}
			String proxyContent = null;
			try {
				proxyContent = new String(aa, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			if (proxyContent != null) {
				Matcher m = IP_PATTERN.matcher(proxyContent);
				String clientIpv4 = null;// 解析我们自定义加入的客户端IP
				if (m.find()) {
					clientIpv4 = m.group();
					return clientIpv4;
				}
			}
		}
		return null;
	}

	@Override
	public void handlerProxyInfo(TBase args, String clientIpv4) {
		logger.debug("设置UserInfo的 IP为:{}", clientIpv4);
		org.apache.thrift.TFieldIdEnum arg0 = args.fieldForId(1);
		if (arg0 != null) {
			// set "userInfo.mobileInfo.networkMcnc" to clientIpv4
		}
	}

}
