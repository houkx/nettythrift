/**
 * 
 */
package io.nettythrift.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.thrift.TBase;

/**
 * @author HouKx
 *
 */
public class DefaultTrafficForecastImpl implements TrafficForecast {

	private HashMap<Integer, MethodTraffic> traffics;
    
	public DefaultTrafficForecastImpl(Map<String, Integer> inits, int statisticSum) {
		this.traffics = new HashMap<>(inits.size());
		for (Map.Entry<String, Integer> entry : inits.entrySet()) {
			traffics.put(entry.getKey().hashCode(), new MethodTraffic(entry.getValue(), statisticSum));
		}
	}

	public int getInitBytesForWrite(String method) {
		MethodTraffic mt = traffics.get(method.hashCode());
		if (mt != null) {
			return mt.avg;
		}
		return 1024;
	}

	@SuppressWarnings("rawtypes")
	public void saveWritedBytes(String method, int writedBytes, TBase args, TBase result) {
		MethodTraffic mt = traffics.get(method.hashCode());
		if (mt != null) {
			mt.save(writedBytes);
		}
	}

	private static class MethodTraffic {
		volatile int avg;
		long lastSum;
		int[] history;

		MethodTraffic(int initMaxBytes, int statisticSum) {
			avg = initMaxBytes;
			history = new int[statisticSum + 1];
			Arrays.fill(history, 1, statisticSum + 1, initMaxBytes);
			history[0] = 1;
			lastSum = statisticSum * initMaxBytes;
		}

		synchronized void save(int writedBytes) {
			int index = history[0]++;
			final int LENGTH = history.length - 1;
			if (index == LENGTH) {
				history[0] = 1;
			}
			int old = history[index];
			history[index] = writedBytes;
			lastSum += writedBytes - old;
			avg = (int) Math.ceil(lastSum * 1.0 / LENGTH);
		}
	}
}
