package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test verifies that the interrupted flag is set correctly by the {@link ExecutionCoordinator} by
 * showing that by considering the interrupted flag tasks will terminate earlier than without.
 */
class StopReactionTest
{
	private static final long	TIME_UNTIL_EXCEPTION_MS	= 500;
	private static final long	TASK_2_SLEEP_INTERVAL	= 100;
	private static final int	TASK_2_SLEEP_REPETITION	= 20;
	private static final long	PRECISION_MS			= 300;

	static Object getParameters() {
		return new Object[]{false, true};
	}

	@ParameterizedTest(name = "react to stop: {0}")
	@MethodSource("getParameters")
	void testStopWithoutStopReaction(boolean reactToStop) {
		TestUtils.waitForEmptyCommonForkJoinPool();
		boolean caughtException = false;
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(this::run1);
			coordinator.execute(() -> run2(reactToStop));
		} catch (ExpectedException e) {
			caughtException = true;
		}
		long elapsedTimeCoordinatorMs = stopWatch.getElapsedTimeMs();
		TestUtils.waitForEmptyCommonForkJoinPool();
		long elapsedTimePoolMs = stopWatch.getElapsedTimeMs();

		Assertions.assertTrue(caughtException, "An exception has been swallowed");

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
		long expectedTimeCoordinatorMs = !reactToStop
			? TASK_2_SLEEP_REPETITION * TASK_2_SLEEP_INTERVAL
			: TIME_UNTIL_EXCEPTION_MS;
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

	private void run2(boolean reactToStop) {
		for (int i = 0; i < TASK_2_SLEEP_REPETITION; i++) {
			if (reactToStop && Thread.currentThread().isInterrupted()) {
				return;
			}
			TestUtils.simulateWork(TASK_2_SLEEP_INTERVAL);
		}
	}

	private static class ExpectedException extends Exception {}
}
