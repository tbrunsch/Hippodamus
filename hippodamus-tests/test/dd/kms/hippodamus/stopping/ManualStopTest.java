package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalRunnable;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * This test verifies that manually stopping the coordinator works correctly.
 */
public class ManualStopTest
{
	private static final long	TASK_SLEEP_INTERVAL		= 100;
	private static final int	TASK_SLEEP_REPETITION	= 20;
	private static final long	PRECISION_MS			= 500;
	private static final long	TIME_UNTIL_STOP_MS		= 1000;

	@Test
	public void testManualStop() {
		Assume.assumeTrue("This test requires at least two tasks to be processed in parallel", TestUtils.getDefaultParallelism() > 1);

		TestUtils.waitForEmptyCommonForkJoinPool();
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(this::runWithStopReaction);
			coordinator.execute(() -> {
				TestUtils.simulateWork(TIME_UNTIL_STOP_MS);
				coordinator.stop();
			});
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();

		long lowerBoundMs = TIME_UNTIL_STOP_MS;
		long upperBoundMs = lowerBoundMs + TASK_SLEEP_INTERVAL + PRECISION_MS;
		TestUtils.assertTimeLowerBound(lowerBoundMs, elapsedTimeMs);
		TestUtils.assertTimeUpperBound(upperBoundMs, elapsedTimeMs);
	}

	private void runWithStopReaction(Supplier<Boolean> stopFlag) {
		for (int i = 0; i < TASK_SLEEP_REPETITION; i++) {
			if (stopFlag.get()) {
				return;
			}
			TestUtils.simulateWork(TASK_SLEEP_INTERVAL);
		}
	}
}
