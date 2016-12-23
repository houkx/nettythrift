/**
 * 
 */
package io.nettythrift.core;

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

	void saveWritedBytes(String method, int writedBytes);
}
