package dd.kms.hippodamus;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Supplier;

// TODO: Document stoppability
/**
 * By default, the {@link dd.kms.hippodamus.coordinator.ExecutionCoordinator} uses the common
 * {@link java.util.concurrent.ForkJoinPool} to execute tasks. Since this {@link java.util.concurrent.ExecutorService}
 * cannot be shut down, submitted tasks cannot be stopped, but run until end. The interfaces
 * {@link dd.kms.hippodamus.exceptions.StoppableExceptionalRunnable} and {@link dd.kms.hippodamus.exceptions.StoppableExceptionalCallable}
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

		/*
		 * The coordinator requests tasks to stop if it encounters an exception, but
		 * it does not wait for them to stop. If submitted tasks themselves to not check
		 * whether they should stop, then they will run until end.
		 */
		long expectedTimeCoordinatorMs = TIME_UNTIL_EXCEPTION_MS;
		Assert.assertTrue("Coordinator finished too early", expectedTimeCoordinatorMs <= elapsedTimeCoordinatorMs);
		Assert.assertTrue("Coordinator finished too late", elapsedTimeCoordinatorMs <= expectedTimeCoordinatorMs + PRECISION_MS);

		long expectedTimePoolMs = TASK_2_SLEEP_REPETITION * TASK_2_SLEEP_INTERVAL;
		Assert.assertTrue("Coordinator finished too early", expectedTimePoolMs <= elapsedTimePoolMs);
		Assert.assertTrue("Coordinator finished too late", elapsedTimePoolMs <= expectedTimePoolMs + PRECISION_MS);

		Assert.assertTrue("An exception has been swallowed", caughtException);
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

		/*
		 * Since the task listens to whether it should stop or not, both, elapsedTimeCoordinatorMs
		 * and elapsedTimePoolMs should both be approximately TIME_UNTIL_EXCEPTION_MS.
		 */
		long expectedTimeMs = TIME_UNTIL_EXCEPTION_MS;
		Assert.assertTrue("Coordinator finished too early", expectedTimeMs <= elapsedTimeCoordinatorMs);
		Assert.assertTrue("Coordinator finished too late", elapsedTimePoolMs <= expectedTimeMs + PRECISION_MS);

		Assert.assertTrue("An exception has been swallowed", caughtException);
	}

	private void run1() throws ExpectedException {
		TestUtils.sleep(TIME_UNTIL_EXCEPTION_MS);
		throw new ExpectedException();
	}

	private void run2WithoutStopReaction() {
		for (int i = 0; i < TASK_2_SLEEP_REPETITION; i++) {
			TestUtils.sleep(TASK_2_SLEEP_INTERVAL);
		}
	}

	private void run2WithStopReaction(Supplier<Boolean> stopFlag) {
		for (int i = 0; i < TASK_2_SLEEP_REPETITION; i++) {
			if (stopFlag.get()) {
				return;
			}
			TestUtils.sleep(TASK_2_SLEEP_INTERVAL);
		}
	}

	private static class ExpectedException extends Exception {}
}
