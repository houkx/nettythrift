package io.nettythrift;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用JavaSocket编写发送HTTP_POST请求的工具类
 *
 * @author 玄玉<http://blog.csdn.net/jadyer>
 * @create Apr 4, 2013 8:37:44 PM
 * @see 与之类似的还有一个HttpClientUtil工具类
 * @see 地址为http://blog.csdn.net/jadyer/article/details/8087960
 * @see 还有一个使用Java原生API编写发送HTTP_POST请求的工具类
 * @see 地址为http://blog.csdn.net/jadyer/article/details/8637228
 */
public class HTTPUtil {

    static void printResult(Map<String, String> respMap) {
        System.out.println("=============================================================================");
        System.out.println("请求报文如下");
        System.out.println(respMap.get("reqMsg"));
        System.out.println("=============================================================================");
        System.out.println("响应报文如下");
        System.out.println(respMap.get("respMsg"));
        System.out.println("=============================================================================");
        System.out.println("响应十六进制如下");
        System.out.println(respMap.get("respMsgHex"));
        System.out.println("=============================================================================");
    }

    /**
     * 发送HTTP_POST请求
     *
     * @param reqURL     请求地址
     * @param reqParams  请求正文数据
     * @param reqCharset 请求报文的编码字符集(主要针对请求参数值含中文而言)
     * @return reqMsg-->HTTP请求完整报文,respMsg-->HTTP响应完整报文,respMsgHex-->HTTP响应的原始字节的十六进制表示
     * @see 本方法默认的连接超时和读取超时均为30秒
     * @see 请求参数含有中文时,亦可直接传入本方法中,本方法内部会自动根据reqCharset参数进行<code>URLEncoder.encode()</code>
     * @see 解码响应正文时,默认取响应头[Content-Type=text/html;
     * charset=GBK]字符集,若无Content-Type,则使用UTF-8解码
     */
    public static Map<String, String> sendPostRequest(String reqURL, Map<String, String> reqParams, String reqCharset) {
        return sendPostRequest(reqURL, reqParams, reqCharset, null);
    }

    public static Map<String, String> sendPostRequest(String reqURL, Map<String, String> reqParams, String reqCharset,
                                                      String head) {
        StringBuilder reqData = new StringBuilder();
        for (Map.Entry<String, String> entry : reqParams.entrySet()) {
            try {
                String key = entry.getKey();
                if (key != null && key.length() > 0) {
                    reqData.append(key).append("=");
                }
                reqData.append(entry.getValue()).append("&");
                // reqData.append(URLEncoder.encode(entry.getValue(),
                // reqCharset)).append("&");
            } catch (Exception e) {
                System.out.println("编码字符串[" + entry.getValue() + "]时发生异常:系统不支持该字符集[" + reqCharset + "]");
                reqData.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
        }
        if (reqData.length() > 0) {
            reqData.setLength(reqData.length() - 1); // 删除最后一个&符号
        }

        return sendPostRequest(reqURL, reqData.toString(), reqCharset, head);
    }

    /**
     * 发送HTTP_POST请求
     *
     * @see you can see {@link HTTPUtil#sendPostRequest(String, Map, String)}
     * @see 注意:若欲直接调用本方法,切记请求参数值含中文时,一定要对该参数值<code>URLEncoder.encode(value, reqCharset)</code>
     * @see 注意:这里只是对key=value中的'value'进行encode,而非'key='..encode完毕后,再组织成key=newValue传给本方法
     */
    public static Map<String, String> sendPostRequest(String reqURL, String reqData, String reqCharset, String head) {
        Map<String, String> respMap = new HashMap<String, String>();
        OutputStream out = null; // 写
        InputStream in = null; // 读
        Socket socket = null; // 客户机
        String respMsg = null;
        String respMsgHex = null;
        String respCharset = "UTF-8";
        StringBuilder reqMsg = new StringBuilder();
        try {
            URL sendURL = new URL(reqURL);
            String host = sendURL.getHost();
            int port = sendURL.getPort() == -1 ? 80 : sendURL.getPort();
            /**
             * 创建Socket
             *
             * @see ---------------------------------------------------------------------------------------------------
             * @see 通过有参构造方法创建Socket对象时,客户机就已经发出了网络连接请求,连接成功则返回Socket对象,反之抛IOException
             * @see 客户端在连接服务器时,也要进行通讯,客户端也需要分配一个端口,这个端口在客户端程序中不曾指定
             * @see 这时就由客户端操作系统自动分配一个空闲的端口,默认的是自动的连续分配
             * @see 如服务器端一直运行着,而客户端不停的重复运行,就会发现默认分配的端口是连续分配的
             * @see 即使客户端程序已经退出了,系统也没有立即重复使用先前的端口
             * @see socket = new Socket(host, port);
             * @see ---------------------------------------------------------------------------------------------------
             * @see 不过,可以通过下面的方式显式的设定客户端的IP和Port
             * @see socket = new Socket(host, port,
             *      InetAddress.getByName("127.0.0.1"), 8765);
             * @see ---------------------------------------------------------------------------------------------------
             *
             *      核心代碼: 1) 產生socket 對象:socket = new Socket(); 2) socket 對象獲取連接
             *      out = socket.getOutputStream(); 3) 根據sock 獲取的連接流發送http post
             *      請求 out.write(reqMsg.toString().getBytes()); 4) 獲取服務端的相應: in
             *      = socket.getInputStream(); 5) 合理處理IO 流: 開啟和關閉
             */
            socket = new Socket();
            // configSocket(socket);

            /**
             * 连接服务端
             */
            // 客户端的Socket构造方法请求与服务器连接时,可能要等待一段时间
            // 默认的Socket构造方法会一直等待下去,直到连接成功,或者出现异常
            // 若欲设定这个等待时间,就要像下面这样使用不带参数的Socket构造方法,单位是毫秒
            // 若超过下面设置的30秒等待建立连接的超时时间,则会抛出SocketTimeoutException
            // 注意:如果超时时间设为0,则表示永远不会超时
            socket.connect(new InetSocketAddress(host, port), 6000);
            if (head != null) {
                reqMsg.append(head);
            }
            /**
             * 构造HTTP请求报文
             */
            String path = sendURL.getPath();
            reqMsg.append("POST ").append((path != null && path.length() > 0) ? path : "/").append(" HTTP/1.1\r\n");
            reqMsg.append("Cache-Control: no-cache\r\n");
            reqMsg.append("Pragma: no-cache\r\n");
            reqMsg.append("User-Agent: JavaSocket/").append(System.getProperty("java.version")).append("\r\n");
            reqMsg.append("Host: ").append(sendURL.getHost()).append("\r\n");
            reqMsg.append("Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2\r\n");
            reqMsg.append("Connection: keep-alive\r\n");
            reqMsg.append("Content-Type: application/x-www-form-urlencoded; charset=").append(reqCharset)
                    .append("\r\n");
            reqMsg.append("Content-Length: ").append(reqData.getBytes().length).append("\r\n");
            reqMsg.append("\r\n");
            reqMsg.append(reqData);

            /**
             * 发送HTTP请求
             */
            out = socket.getOutputStream();
            // 这里针对getBytes()补充一下:之所以没有在该方法中指明字符集(包括上面头信息组装Content-Length的时候)
            // 是因为传进来的请求正文里面不会含中文,而非中文的英文字母符号等等,其getBytes()无论是否指明字符集,得到的都是内容一样的字节数组
            // 所以更建议不要直接调用本方法,而是通过sendPostRequest(String, Map<String, String>,
            // String)间接调用本方法
            // sendPostRequest(.., Map,
            // ..)在调用本方法前,会自动对请求参数值进行URLEncoder(注意不包括key=value中的'key=')
            // 而该方法的第三个参数reqCharset只是为了拼装HTTP请求头信息用的,目的是告诉服务器使用哪种字符集解码HTTP请求报文里面的中文信息
            out.write(reqMsg.toString().getBytes());

            /**
             * 接收HTTP响应
             */
            in = socket.getInputStream();
            // 事实上就像JDK的API所述:Closing a ByteArrayOutputStream has no effect
            // 查询ByteArrayOutputStream.close()的源码会发现,它没有做任何事情,所以其close()与否是无所谓的
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int len = -1;

            while ((len = in.read(buffer)) != -1) {
                // 将读取到的字节写到ByteArrayOutputStream中
                // 所以最终ByteArrayOutputStream的字节数应该等于HTTP响应报文的整体长度,而大于HTTP响应正文的长度
                bytesOut.write(buffer, 0, len);
                if (len < buffer.length) {
                    break;
                }
            }
            // 响应的原始字节数组
            byte[] respBuffer = bytesOut.toByteArray();
            respMsgHex = formatToHexStringWithASCII(respBuffer);

            /**
             * 获取Content-Type中的charset值(Content-Type: text/html; charset=GBK)
             */
            int from = 0;
            int to = 0;
            for (int i = 0; i < respBuffer.length; i++) {
                if ((respBuffer[i] == 99 || respBuffer[i] == 67)
                        && (respBuffer[i + 1] == 111 || respBuffer[i + 1] == 79)
                        && (respBuffer[i + 2] == 110 || respBuffer[i + 2] == 78)
                        && (respBuffer[i + 3] == 116 || respBuffer[i + 3] == 84)
                        && (respBuffer[i + 4] == 101 || respBuffer[i + 4] == 69)
                        && (respBuffer[i + 5] == 110 || respBuffer[i + 5] == 78)
                        && (respBuffer[i + 6] == 116 || respBuffer[i + 6] == 84) && respBuffer[i + 7] == 45
                        && (respBuffer[i + 8] == 84 || respBuffer[i + 8] == 116)
                        && (respBuffer[i + 9] == 121 || respBuffer[i + 9] == 89)
                        && (respBuffer[i + 10] == 112 || respBuffer[i + 10] == 80)
                        && (respBuffer[i + 11] == 101 || respBuffer[i + 11] == 69)) {
                    from = i;
                    // 既然匹配到了Content-Type,那就一定不会匹配到我们想到的\r\n,所以就直接跳到下一次循环中喽..
                    continue;
                }
                if (from > 0 && to == 0 && respBuffer[i] == 13 && respBuffer[i + 1] == 10) {
                    // 一定要加to==0限制,因为可能存在Content-Type后面还有其它的头信息
                    to = i;
                    // 既然得到了你想得到的,那就不要再循环啦,徒做无用功而已
                    break;
                }
            }
            // 解码HTTP响应头中的Content-Type
            byte[] headerByte = Arrays.copyOfRange(respBuffer, from, to);
            // HTTP响应头信息无中文,用啥解码都可以
            String contentType = new String(headerByte);
            // 提取charset值
            if (contentType.toLowerCase().contains("charset")) {
                respCharset = contentType.substring(contentType.lastIndexOf("=") + 1);
            }
            /**
             * 解码HTTP响应的完整报文
             */
            respMsg = bytesOut.toString(respCharset);
        } catch (Exception e) {
            System.out.println("与[" + reqURL + "]通信遇到异常,堆栈信息如下");
            e.printStackTrace();
        } finally {
            if (null != socket && socket.isConnected() && !socket.isClosed()) {
                try {
                    // 此时socket的输出流和输入流也都会被关闭
                    // 值得注意的是:先后调用Socket的shutdownInput()和shutdownOutput()方法
                    // 值得注意的是:仅仅关闭了输入流和输出流,并不等价于调用Socket.close()方法
                    // 通信结束后,仍然要调用Socket.close()方法,因为只有该方法才会释放Socket占用的资源,如占用的本地端口等
                    socket.close();
                } catch (IOException e) {
                    System.out.println("关闭客户机Socket时发生异常,堆栈信息如下");
                    e.printStackTrace();
                }
            }
        }
        respMap.put("reqMsg", reqMsg.toString());
        respMap.put("respMsg", respMsg);
        respMap.put("respMsgHex", respMsgHex);
        return respMap;
    }

    private static void configSocket(Socket socket) throws SocketException {
        /**
         * 设置Socket属性
         */
        // true表示关闭Socket的缓冲,立即发送数据..其默认值为false
        // 若Socket的底层实现不支持TCP_NODELAY选项,则会抛出SocketException
        socket.setTcpNoDelay(true);
        // 表示是否允许重用Socket所绑定的本地地址
        socket.setReuseAddress(true);
        // 表示接收数据时的等待超时时间,单位毫秒..其默认值为0,表示会无限等待,永远不会超时
        // 当通过Socket的输入流读数据时,如果还没有数据,就会等待
        // 超时后会抛出SocketTimeoutException,且抛出该异常后Socket仍然是连接的,可以尝试再次读数据
        socket.setSoTimeout(5000);
        // 表示当执行Socket.close()时,是否立即关闭底层的Socket
        // 这里设置为当Socket关闭后,底层Socket延迟5秒后再关闭,而5秒后所有未发送完的剩余数据也会被丢弃
        // 默认情况下,执行Socket.close()方法,该方法会立即返回,但底层的Socket实际上并不立即关闭
        // 它会延迟一段时间,直到发送完所有剩余的数据,才会真正关闭Socket,断开连接
        // Tips:当程序通过输出流写数据时,仅仅表示程序向网络提交了一批数据,由网络负责输送到接收方
        // Tips:当程序关闭Socket,有可能这批数据还在网络上传输,还未到达接收方
        // Tips:这里所说的"未发送完的剩余数据"就是指这种还在网络上传输,未被接收方接收的数据
        socket.setSoLinger(true, 5);
        // 表示发送数据的缓冲区的大小
        socket.setSendBufferSize(1024);
        // 表示接收数据的缓冲区的大小
        socket.setReceiveBufferSize(1024);
        // 表示对于长时间处于空闲状态(连接的两端没有互相传送数据)的Socket,是否要自动把它关闭,true为是
        // 其默认值为false,表示TCP不会监视连接是否有效,不活动的客户端可能会永久存在下去,而不会注意到服务器已经崩溃
        socket.setKeepAlive(true);
        // 表示是否支持发送一个字节的TCP紧急数据,socket.sendUrgentData(data)用于发送一个字节的TCP紧急数据
        // 其默认为false,即接收方收到紧急数据时不作任何处理,直接将其丢弃..若用户希望发送紧急数据,则应设其为true
        // 设为true后,接收方会把收到的紧急数据与普通数据放在同样的队列中
        socket.setOOBInline(true);
        // 该方法用于设置服务类型,以下代码请求高可靠性和最小延迟传输服务(把0x04与0x10进行位或运算)
        // Socket类用4个整数表示服务类型
        // 0x02:低成本(二进制的倒数第二位为1)
        // 0x04:高可靠性(二进制的倒数第三位为1)
        // 0x08:最高吞吐量(二进制的倒数第四位为1)
        // 0x10:最小延迟(二进制的倒数第五位为1)
        socket.setTrafficClass(0x04 | 0x10);
        // 该方法用于设定连接时间,延迟,带宽的相对重要性(该方法的三个参数表示网络传输数据的3项指标)
        // connectionTime--该参数表示用最少时间建立连接
        // latency---------该参数表示最小延迟
        // bandwidth-------该参数表示最高带宽
        // 可以为这些参数赋予任意整数值,这些整数之间的相对大小就决定了相应参数的相对重要性
        // 如这里设置的就是---最高带宽最重要,其次是最小连接时间,最后是最小延迟
        socket.setPerformancePreferences(2, 1, 3);
    }

    /**
     * 通过ASCII码将十进制的字节数组格式化为十六进制字符串
     *
     * @see 该方法会将字节数组中的所有字节均格式化为字符串
     * @see 使用说明详见<code>formatToHexStringWithASCII(byte[], int, int)</code>方法
     */
    private static String formatToHexStringWithASCII(byte[] data) {
        return formatToHexStringWithASCII(data, 0, data.length);
    }

    /**
     * 通过ASCII码将十进制的字节数组格式化为十六进制字符串
     *
     * @param data   十进制的字节数组
     * @param offset 数组下标,标记从数组的第几个字节开始格式化输出
     * @param length 格式长度,其不得大于数组长度,否则抛出java.lang.ArrayIndexOutOfBoundsException
     * @return 格式化后的十六进制字符串
     * @see 该方法常用于字符串的十六进制打印,打印时左侧为十六进制数值,右侧为对应的字符串原文
     * @see 在构造右侧的字符串原文时,该方法内部使用的是平台的默认字符集,来解码byte[]数组
     * @see 该方法在将字节转为十六进制时,默认使用的是<code>java.util.Locale.getDefault()</code>
     * @see 详见String.format(String, Object...)方法和new String(byte[], int,
     * int)构造方法
     */
    private static String formatToHexStringWithASCII(byte[] data, int offset, int length) {
        int end = offset + length;
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        sb.append("\r\n------------------------------------------------------------------------");
        boolean chineseCutFlag = false;

        for (int i = offset; i < end; i += 16) {
            sb.append(String.format("\r\n%04X: ", i - offset)); // X或x表示将结果格式化为十六进制整数
            sb2.setLength(0);
            for (int j = i; j < i + 16; j++) {
                if (j < end) {
                    byte b = data[j];
                    if (b >= 0) { // ENG ASCII
                        sb.append(String.format("%02X ", b));
                        if (b < 32 || b > 126) { // 不可见字符
                            sb2.append(" ");
                        } else {
                            sb2.append((char) b);
                        }
                    } else { // CHA ASCII
                        if (j == i + 15) { // 汉字前半个字节
                            sb.append(String.format("%02X ", data[j]));
                            chineseCutFlag = true;
                            String s = new String(data, j, 2);
                            sb2.append(s);
                        } else if (j == i && chineseCutFlag) { // 后半个字节
                            sb.append(String.format("%02X ", data[j]));
                            chineseCutFlag = false;
                            String s = new String(data, j, 1);
                            sb2.append(s);
                        } else {
                            sb.append(String.format("%02X %02X ", data[j], data[j + 1]));
                            String s = new String(data, j, 2);
                            sb2.append(s);
                            j++;
                        }
                    }
                } else {
                    sb.append("   ");
                }
            }
            sb.append("| ");
            sb.append(sb2.toString());
        }
        sb.append("\r\n------------------------------------------------------------------------");
        return sb.toString();
    }

}
