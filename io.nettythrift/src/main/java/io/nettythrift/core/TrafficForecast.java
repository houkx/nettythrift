/**
 * 
 */
package io.nettythrift.core;

import org.apache.thrift.TBase;

/**
 * guess the Traffic for next invocation <br/>
 * 流量(下发)预测
 * <p>
 * 
 * 
 * @author HouKx
 *
 */
public interface TrafficForecast {
	int getInitBytesForWrite(String method);

	@SuppressWarnings("rawtypes")
	void saveWritedBytes(String method, int writedBytes, TBase args, TBase result);
}
