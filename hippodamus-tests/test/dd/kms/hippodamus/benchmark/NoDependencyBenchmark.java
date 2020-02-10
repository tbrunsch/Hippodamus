package dd.kms.hippodamus.benchmark;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * One of the main purposes of {@link ExecutionCoordinator}s is
 * handling exceptions and dependencies. If there are no dependencies, then other approaches
 * with less overhead may be a better alternative. However, we want to ensure that the
 * framework overhead is not too large.
 */
@RunWith(Parameterized.class)
public class NoDependencyBenchmark
{
	private static final long	PRECISION_MS	= 200;
	private static final double	TOLERANCE		= 1.05;

	private final int	numTasks;
	private final long	taskTimeMs;

	public NoDependencyBenchmark(int numTasks, long taskTimeMs) {
		this.numTasks = numTasks;
		this.taskTimeMs = taskTimeMs;
	}

	@Parameterized.Parameters(name = "#tasks: {0}, task duration: {1} ms")
	public static Object getParameters() {
		return Arrays.asList(
			new Object[]{ 1, 100 },
			new Object[]{ 1, 1000 },
			new Object[]{ 10, 100 },
			new Object[]{ 10, 1000 },
			new Object[]{ 100, 100 },
			new Object[]{ 1000, 10 }
		);
	}

	@Test
	public void benchmarkNoDependencies() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		long threadsTimeMs = BenchmarkUtils.measureTime(this::runWithDedicatedThreads);
		TestUtils.waitForEmptyCommonForkJoinPool();
		long futureTimeMs = BenchmarkUtils.measureTime(this::runWithCompletableFutures);
		TestUtils.waitForEmptyCommonForkJoinPool();
		long coordinatorTimeMs = BenchmarkUtils.measureTime(this::runWithCoordinator);

		System.out.println(MessageFormat.format("Times (threads/futures/coordinator): {0} ms/{1} ms/{2} ms", threadsTimeMs, futureTimeMs, coordinatorTimeMs));

		long minTimeMs = Math.min(threadsTimeMs, futureTimeMs);
		long maxAllowedTimeMs = Math.round(TOLERANCE*minTimeMs + PRECISION_MS);

		TestUtils.assertTimeUpperBound(maxAllowedTimeMs, coordinatorTimeMs);
	}

	private void runWithDedicatedThreads() {
		List<Thread> threads = new ArrayList<>(numTasks);
		for (int i = 0; i < numTasks; i++) {
			Thread thread = new Thread(this::runTask);
			thread.run();
			threads.add(thread);
		}
		boolean exception = false;
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				exception = true;
			}
		}
		Assert.assertFalse("An interrupted exception occurred in the thread approach", exception);
	}

	private void runWithCompletableFutures() {
		List<Future> futures = new ArrayList<>(numTasks);
		for (int i = 0; i < numTasks; i++) {
			Future future = CompletableFuture.runAsync(this::runTask);
			futures.add(future);
		}
		boolean exception = false;
		for (Future future : futures) {
			try {
				future.get();
			} catch (Exception e) {
				exception = true;
			}
		}
		Assert.assertFalse("An interrupted exception occurred in the thread approach", exception);
	}

	private void runWithCoordinator() {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < numTasks; i++) {
				coordinator.execute(this::runTask);
			}
		}
	}

	private void runTask() {
		TestUtils.sleep(taskTimeMs);
	}
}
