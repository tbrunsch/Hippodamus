package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static dd.kms.hippodamus.testUtils.TestUtils.BOOLEANS;

/**
 * This test focuses on the behavior of the framework in case of exceptions
 * occurring where the framework has no chance to handle them.
 */
class IntractableExceptionTest
{
	private static final int	TASK_DURATION_MS	= 1000;
	private static final int	PRECISION_MS		= 200;

	/**
	 * This test demonstrates that all tasks will run to completion although an
	 * exception has been thrown inside the try-block. The reason for this is
	 * that the coordinator does not get informed about this exception and
	 * the coordinator's close method will be called before the code inside the
	 * catch-clause is executed.
	 */
	@Test
	void testIntractableException() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		StopWatch stopWatch = new StopWatch();
		boolean caughtIntractableException = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> TestUtils.simulateWork(TASK_DURATION_MS));
			throwIntractableException(true);
		} catch (IntractableException e) {
			caughtIntractableException = true;
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();

		Assertions.assertTrue(caughtIntractableException, "No exception has been thrown");

		TestUtils.assertTimeBounds(TASK_DURATION_MS, PRECISION_MS, elapsedTimeMs);
	}

	/**
	 * This test demonstrates how to protecting against the time loss due to intractable exceptions
	 * by guarding the code inside the try-block with {@code coordinator.permitTaskSubmission()}.
	 * The disadvantage is that no task will be executed until the end of the try-block.
	 */
	@Test
	void testIntractableExceptionWithImmediateStop() {
		for (boolean throwException : BOOLEANS) {
			TestUtils.waitForEmptyCommonForkJoinPool();
			StopWatch stopWatch = new StopWatch();
			boolean caughtIntractableException = false;
			try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
				coordinator.permitTaskSubmission(false);
				coordinator.execute(() -> TestUtils.simulateWork(TASK_DURATION_MS));
				throwIntractableException(throwException);
				coordinator.permitTaskSubmission(true);
			} catch (IntractableException e) {
				caughtIntractableException = true;
			}
			long elapsedTimeMs = stopWatch.getElapsedTimeMs();

			if (throwException) {
				Assertions.assertTrue(caughtIntractableException, "No exception has been thrown");

				TestUtils.assertTimeUpperBound(PRECISION_MS, elapsedTimeMs);
			} else {
				Assertions.assertFalse(caughtIntractableException, "An exception has been thrown");

				TestUtils.assertTimeBounds(TASK_DURATION_MS, PRECISION_MS, elapsedTimeMs);
			}
		}
	}

	private void throwIntractableException(boolean throwException) {
		if (throwException) {
			throw new IntractableException();
		}
	}

	private static class IntractableException extends RuntimeException {}
}
