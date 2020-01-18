package dd.kms.hippodamus.benchmark;

import dd.kms.hippodamus.testUtils.StopWatch;

class BenchmarkUtils
{
	static long measureTime(Runnable runnable) {
		StopWatch stopWatch = new StopWatch();
		runnable.run();
		return stopWatch.getElapsedTimeMs();
	}
}
