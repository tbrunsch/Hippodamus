package dd.kms.hippodamus;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalRunnable;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * By default, the {@link ExecutionCoordinator} uses the common
 * {@link ForkJoinPool} to execute tasks. Since this {@link ExecutorService}
 * cannot be shut down, submitted tasks cannot be stopped, but run until end. The interfaces
 * {@link StoppableExceptionalRunnable} and {@link StoppableExceptionalCallable}
 * allow implementers of a task to query whether the task should be stopped.<br/>
 * <br/>
 * This test verifies that reacting to a stop request indeed has an effect.
 */
public class StoppableTest
{
	private static final long	TIME_UNTIL_EXCEPTION_MS	= 500;
	private static final long	TASK_2_SLEEP_INTERVAL	= 100;
	private static final int	TASK_2_SLEEP_REPETITION	= 20;
	private static final long	PRECISION_MS			= 500;
	private static final long	TIME_UNTIL_STOP_MS		= 1000;

	@Test
	public void testStopWithoutStopReaction() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		boolean caughtException = false;
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(this::run1);
			coordinator.execute(this::run2WithoutStopReaction);
		} catch (ExpectedException e) {
			caughtException = true;
		}
		long elapsedTimeCoordinatorMs = stopWatch.getElapsedTimeMs();
		TestUtils.waitForEmptyCommonForkJoinPool();
		long elapsedTimePoolMs = stopWatch.getElapsedTimeMs();

		Assert.assertTrue("An exception has been swallowed", caughtException);

		if (TestUtils.getDefaultParallelism() < 2) {
			// We do not require time constraints to be met with only 1 processor
			System.out.println("Skipped checking time constraints");
			return;
		}

		/*
		 * The coordinator requests tasks to stop if it encounters an exception, but
		 * it does not wait for them to stop. If submitted tasks themselves to not check
		 * whether they should stop, then they will run until end.
		 */
		long expectedTimeCoordinatorMs = TIME_UNTIL_EXCEPTION_MS;
		TestUtils.assertTimeLowerBound(expectedTimeCoordinatorMs, elapsedTimeCoordinatorMs);
		TestUtils.assertTimeUpperBound(expectedTimeCoordinatorMs + PRECISION_MS, elapsedTimeCoordinatorMs);

		long expectedTimePoolMs = TASK_2_SLEEP_REPETITION * TASK_2_SLEEP_INTERVAL;
		TestUtils.assertTimeLowerBound(expectedTimePoolMs, elapsedTimePoolMs);
		TestUtils.assertTimeUpperBound(expectedTimePoolMs + PRECISION_MS, elapsedTimePoolMs);
	}

	@Test
	public void testStopWithStopReaction() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		boolean caughtException = false;
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(this::run1);
			coordinator.execute(this::run2WithStopReaction);
		} catch (ExpectedException e) {
			caughtException = true;
		}
		long elapsedTimeCoordinatorMs = stopWatch.getElapsedTimeMs();
		TestUtils.waitForEmptyCommonForkJoinPool();
		long elapsedTimePoolMs = stopWatch.getElapsedTimeMs();

		Assert.assertTrue("An exception has been swallowed", caughtException);

		if (TestUtils.getDefaultParallelism() < 2) {
			// We do not require time constraints to be met with only 1 processor
			System.out.println("Skipped checking time constraints");
			return;
		}

		/*
		 * Since the task listens to whether it should stop or not, both, elapsedTimeCoordinatorMs
		 * and elapsedTimePoolMs should both be approximately TIME_UNTIL_EXCEPTION_MS.
		 */
		long expectedTimeMs = TIME_UNTIL_EXCEPTION_MS;
		TestUtils.assertTimeLowerBound(expectedTimeMs, elapsedTimeCoordinatorMs);
		TestUtils.assertTimeUpperBound(expectedTimeMs + PRECISION_MS, elapsedTimePoolMs);
	}

	@Test
	public void testManualStop() {
		Assume.assumeTrue("This test requires at least two tasks to be processed in parallel", TestUtils.getDefaultParallelism() > 1);

		TestUtils.waitForEmptyCommonForkJoinPool();
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(this::run2WithStopReaction);
			coordinator.execute(() -> {
				TestUtils.simulateWork(TIME_UNTIL_STOP_MS);
				coordinator.stop();
			});
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();

		long lowerBoundMs = TIME_UNTIL_STOP_MS;
		long upperBoundMs = lowerBoundMs + TASK_2_SLEEP_INTERVAL + PRECISION_MS;
		TestUtils.assertTimeLowerBound(lowerBoundMs, elapsedTimeMs);
		TestUtils.assertTimeUpperBound(upperBoundMs, elapsedTimeMs);
	}

	private void run1() throws ExpectedException {
		TestUtils.simulateWork(TIME_UNTIL_EXCEPTION_MS);
		throw new ExpectedException();
	}

	private void run2WithoutStopReaction() {
		for (int i = 0; i < TASK_2_SLEEP_REPETITION; i++) {
			TestUtils.simulateWork(TASK_2_SLEEP_INTERVAL);
		}
	}

	private void run2WithStopReaction(Supplier<Boolean> stopFlag) {
		for (int i = 0; i < TASK_2_SLEEP_REPETITION; i++) {
			if (stopFlag.get()) {
				return;
			}
			TestUtils.simulateWork(TASK_2_SLEEP_INTERVAL);
		}
	}

	private static class ExpectedException extends Exception {}
}
