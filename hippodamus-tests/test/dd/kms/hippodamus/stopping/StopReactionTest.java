package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalRunnable;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
@RunWith(Parameterized.class)
public class StopReactionTest
{
	private static final long	TIME_UNTIL_EXCEPTION_MS	= 500;
	private static final long	TASK_2_SLEEP_INTERVAL	= 100;
	private static final int	TASK_2_SLEEP_REPETITION	= 20;
	private static final long	PRECISION_MS			= 300;

	@Parameters(name = "react to stop: {0}")
	public static Object getParameters() {
		return new Object[]{ false, true };
	}

	private final boolean reactToStop;

	public StopReactionTest(boolean reactToStop) {
		this.reactToStop = reactToStop;
	}

	@Test
	public void testStopWithoutStopReaction() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		boolean caughtException = false;
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(this::run1);
			coordinator.execute(this::run2);
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

		long expectedPoolLowerBoundMs = reactToStop ? TIME_UNTIL_EXCEPTION_MS : TASK_2_SLEEP_REPETITION * TASK_2_SLEEP_INTERVAL;
		long expectedPoolUpperBoundMs = (reactToStop ? TIME_UNTIL_EXCEPTION_MS + TASK_2_SLEEP_INTERVAL : TASK_2_SLEEP_REPETITION * TASK_2_SLEEP_INTERVAL)
										+ PRECISION_MS;
		TestUtils.assertTimeLowerBound(expectedPoolLowerBoundMs, elapsedTimePoolMs);
		TestUtils.assertTimeUpperBound(expectedPoolUpperBoundMs, elapsedTimePoolMs);
	}

	private void run1() throws ExpectedException {
		TestUtils.simulateWork(TIME_UNTIL_EXCEPTION_MS);
		throw new ExpectedException();
	}

	private void run2(Supplier<Boolean> stopFlag) {
		for (int i = 0; i < TASK_2_SLEEP_REPETITION; i++) {
			if (reactToStop && stopFlag.get()) {
				return;
			}
			TestUtils.simulateWork(TASK_2_SLEEP_INTERVAL);
		}
	}

	private static class ExpectedException extends Exception {}
}
