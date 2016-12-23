package io.nettythrift.core;

import java.util.concurrent.ConcurrentHashMap;

public class DefaultLogicExecutionStatisticsImpl implements LogicExecutionStatistics {
	private final int threshold;
	private final int statisticSum;
	private ConcurrentHashMap<String, Statistics> executionMillTime;

	public DefaultLogicExecutionStatisticsImpl(int threshold, int statisticSum) {
		this.threshold = threshold;
		this.statisticSum = statisticSum;
		executionMillTime = new ConcurrentHashMap<>(128);
	}

	public boolean shouldExecuteInIOThread(String method) {
		Statistics st = executionMillTime.get(method);
		if (st != null) {
			return st.max <= threshold;
		}
		return false;
	}

	public void saveExecutionMillTime(String method, int exeTime) {
		Statistics st = executionMillTime.get(method);
		if (st == null) {
			st = new Statistics((int) Math.ceil((exeTime + 1) * 1.5), statisticSum);
			Statistics old = executionMillTime.putIfAbsent(method, st);
			if (old != null) {
				st = old;
			}
		}
		st.save(exeTime);
	}

	private static class Statistics {
		volatile int max;
		int[] history;
		int indexOfMax;

		Statistics(int init, int statisticSum) {
			max = init;
			history = new int[statisticSum + 1];
			history[0] = indexOfMax = 1;
		}

		synchronized void save(int newVal) {
			int index = history[0]++;
			final int LENGTH = history.length - 1;
			if (index == LENGTH) {
				history[0] = 1;
			}
			history[index] = newVal;
			if (index != indexOfMax) {
				if (newVal > max) {
					indexOfMax = index;
					max = newVal;
				}
			} else if (history[LENGTH] > 0) {
				if (newVal < max) {
					int imax = 1;
					for (int i = 2; i < history.length; i++) {
						if (history[i] > history[imax]) {
							imax = i;
						}
					}
					indexOfMax = imax;
					max = history[imax];
				} else {
					max = newVal;
				}
			}
		}
	}
}
