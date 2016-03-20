/**
 * 
 */
package io.nettythrift;

/**
 * @author houkangxi
 *
 */
public class ResponseCodes {
    /**
	 * （成功） 服务器已成功处理了请求。
	 */
	public static final int CODE_SUCCESS = 200;
	/**
	 * （错误请求） 服务器不理解请求的语法。
	 */
	public static final int CODE_ERR_REQ = 400;
	/**
	 * （请求超时） 服务器等候请求时发生超时。
	 */
	public static final int CODE_REQ_TIMEOUT = 408;
	/**
	 * （请求实体过大） 服务器无法处理请求，因为请求实体过大，超出服务器的处理能力。
	 */
	public static final int CODE_TO_LONG_REQBODY = 413;
	/**
	 * （服务器内部错误） 服务器遇到错误，无法完成请求
	 */
	public static final int CODE_SERVER_INTERNAL_ERR = 500;
	
	/**
	 * 找不到资源
	 */
	public static final int CODE_RES_NOTFOUND = 404;
	/**
	 * 无法接受
	 */
	public static final int CODE_NOT_ACCEPT = 406;
}
