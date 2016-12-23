/**
 * 
 */
package io.nettythrift.core;

/**
 * 逻辑执行(时间)统计
 * 
 * @author HouKx
 *
 */
public interface LogicExecutionStatistics {
	boolean shouldExecuteInIOThread(String method);

	void saveExecutionMillTime(String method, int exeTime);
}
