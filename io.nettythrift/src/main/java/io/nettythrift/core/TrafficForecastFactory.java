/**
 * 
 */
package io.nettythrift.core;

import java.util.Map;

/**
 * @author nq
 *
 */
public interface TrafficForecastFactory {
	TrafficForecast create(Map<String, Integer> inits);
}
