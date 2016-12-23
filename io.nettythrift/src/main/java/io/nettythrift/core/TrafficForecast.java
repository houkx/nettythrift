/**
 * 
 */
package io.nettythrift.core;

/**
 * 流量(下发)预测
 * 
 * @author HouKx
 *
 */
public interface TrafficForecast {
	int getInitBytesForWrite(String method);

	void saveWritedBytes(String method, int writedBytes);
}
