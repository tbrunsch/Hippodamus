package dd.kms.hippodamus.benchmark;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * One of the main purposes of {@link ExecutionCoordinator}s is
 * handling exceptions and dependencies. If no dependencies are specified, then the framework
 * cannot benefit from additional information, but has a higher overhead than other approaches.
 * However, we want to ensure that the framework overhead is not too large.
 */
public class NoSpecifiedDependenciesBenchmark
{
	private static final int	NUM_TASKS		= 1000;
	private static final long	TASK_TIME_MS	= 10;
	private static final long	PRECISION_MS	= 200;
	private static final double	TOLERANCE		= 1.05;

	@Test
	public void benchmarkNoSpecifiedDependencies() {
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
		Assert.assertEquals("Wrong future result", NUM_TASKS, count);
	}

	private void runNoSpecifiedDependenciesWithCoordinator() {
		Supplier<Integer> countSupplier = () -> 0;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < NUM_TASKS; i++) {
				Supplier<Integer> prevSupplier = countSupplier;
				countSupplier = coordinator.execute(() -> plusOne(prevSupplier.get()));
			}
		}
		int count = countSupplier.get();
		Assert.assertEquals("Wrong future result", NUM_TASKS, count);
	}

	private int plusOne(int value) {
		TestUtils.sleepUninterruptibly(TASK_TIME_MS);
		return value + 1;
	}

	private <T> T get(Future<T> future) {
		try {
			return future.get();
		} catch (Exception e) {
			Assert.fail("Exception when calling Future.get(): " + e.getMessage());
			return null;
		}
	}
}
