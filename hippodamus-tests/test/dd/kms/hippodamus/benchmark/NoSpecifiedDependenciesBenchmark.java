package dd.kms.hippodamus.benchmark;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * One of the main purposes of {@link ExecutionCoordinator}s is handling exceptions and dependencies. If no dependencies
 * are specified, then the framework cannot benefit from additional information, but has a higher overhead than other
 * approaches. However, we want to ensure that the framework overhead is not too large.
 */
class NoSpecifiedDependenciesBenchmark
{
	private static final int	NUM_TASKS		= 100;
	private static final long	TASK_TIME_MS	= 100;
	private static final long	PRECISION_MS	= 200;
	private static final double	TOLERANCE		= 1.05;

	@Test
	void benchmarkNoSpecifiedDependencies() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		long futureTimeMs = BenchmarkUtils.measureTime(this::runNoSpecifiedDependenciesWithCompletableFutures);
		TestUtils.waitForEmptyCommonForkJoinPool();
		long coordinatorTimeMs = BenchmarkUtils.measureTime(this::runNoSpecifiedDependenciesWithCoordinator);

		System.out.println(MessageFormat.format("Times (futures/coordinator): {0} ms/{1} ms", futureTimeMs, coordinatorTimeMs));

		long maxAllowedTimeMs = Math.round(TOLERANCE*futureTimeMs + PRECISION_MS);
		TestUtils.assertTimeUpperBound(maxAllowedTimeMs, coordinatorTimeMs);
	}

	private void runNoSpecifiedDependenciesWithCompletableFutures() {
		Future<Integer> future = CompletableFuture.completedFuture(0);
		for (int i = 0; i < NUM_TASKS; i++) {
			Future<Integer> prevFuture = future;
			future = CompletableFuture.supplyAsync(() -> plusOne(get(prevFuture)));
		}
		int count = get(future);
		Assertions.assertEquals(NUM_TASKS, count, "Wrong future result");
	}

	private void runNoSpecifiedDependenciesWithCoordinator() {
		Supplier<Integer> countSupplier = () -> 0;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < NUM_TASKS; i++) {
				Supplier<Integer> prevSupplier = countSupplier;
				ResultHandle<Integer> handle = coordinator.execute(() -> plusOne(prevSupplier.get()));
				countSupplier = handle::get;
			}
		}
		int count = countSupplier.get();
		Assertions.assertEquals(NUM_TASKS, count, "Wrong future result");
	}

	private int plusOne(int value) {
		TestUtils.simulateWork(TASK_TIME_MS);
		return value + 1;
	}

	private int get(Future<Integer> future) {
		try {
			return future.get();
		} catch (Exception e) {
			Assertions.fail("Exception when calling Future.get(): " + e);
			return 0;
		}
	}
}
