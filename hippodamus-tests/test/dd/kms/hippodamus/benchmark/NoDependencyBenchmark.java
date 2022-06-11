package dd.kms.hippodamus.benchmark;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * One of the main purposes of {@link ExecutionCoordinator}s is handling exceptions and dependencies. If there are no
 * dependencies, then other approaches with less overhead may be a better alternative. However, we want to ensure that
 * the framework overhead is not too large.
 */
class NoDependencyBenchmark
{
	private static final long	PRECISION_MS	= 200;
	private static final double	TOLERANCE		= 1.05;

	static Object getParameters() {
		return Arrays.asList(
			new Object[]{ 1, 100 },
			new Object[]{ 1, 1000 },
			new Object[]{ 10, 100 },
			new Object[]{ 10, 1000 },
			new Object[]{ 100, 100 },
			new Object[]{ 1000, 10 }
		);
	}

	@ParameterizedTest(name = "#tasks: {0}, task duration: {1} ms")
	@MethodSource("getParameters")
	void benchmarkNoDependencies(int numTasks, long taskTimeMs) {
		TestUtils.waitForEmptyCommonForkJoinPool();
		long futureTimeMs = BenchmarkUtils.measureTime(() -> runWithCompletableFutures(numTasks, taskTimeMs));
		TestUtils.waitForEmptyCommonForkJoinPool();
		long coordinatorTimeMs = BenchmarkUtils.measureTime(() -> runWithCompletableFutures(numTasks, taskTimeMs));

		System.out.println(MessageFormat.format("Times (futures/coordinator): {0} ms/{1} ms", futureTimeMs, coordinatorTimeMs));

		long maxAllowedTimeMs = Math.round(TOLERANCE*futureTimeMs + PRECISION_MS);

		TestUtils.assertTimeUpperBound(maxAllowedTimeMs, coordinatorTimeMs);
	}

	private void runWithCompletableFutures(int numTasks, long taskTimeMs) {
		List<Future<Void>> futures = new ArrayList<>(numTasks);
		for (int i = 0; i < numTasks; i++) {
			Future<Void> future = CompletableFuture.runAsync(() -> runTask(taskTimeMs));
			futures.add(future);
		}
		boolean exception = false;
		for (Future<Void> future : futures) {
			try {
				future.get();
			} catch (Exception e) {
				exception = true;
			}
		}
		Assertions.assertFalse(exception, "An interrupted exception occurred in the thread approach");
	}

	private void runWithCoordinator(int numTasks, long taskTimeMs) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < numTasks; i++) {
				coordinator.execute(() -> runTask(taskTimeMs));
			}
		}
	}

	private void runTask(long taskTimeMs) {
		TestUtils.simulateWork(taskTimeMs);
	}
}
