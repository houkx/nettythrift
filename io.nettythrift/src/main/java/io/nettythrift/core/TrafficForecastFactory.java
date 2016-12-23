/**
 * 
 */
package io.nettythrift.core;

import java.util.Map;

/**
 * Factory for create 'TrafficForecast'
 * 
 * @author HouKx
 *
 */
public interface TrafficForecastFactory {
	TrafficForecast create(Map<String, Integer> inits);
}
