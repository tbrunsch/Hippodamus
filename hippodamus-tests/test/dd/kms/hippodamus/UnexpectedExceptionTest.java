package dd.kms.hippodamus;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import static dd.kms.hippodamus.testUtils.TestUtils.BOOLEANS;

/**
 * This test focuses on the behavior of the framework in case of exceptions
 * occurring where the framework has no chance to handle them.
 */
public class UnexpectedExceptionTest
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
	public void testUnexpectedException() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		StopWatch stopWatch = new StopWatch();
		boolean caughtUnexpectedException = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> TestUtils.sleep(TASK_DURATION_MS));
			throwUnexpectedException(true);
		} catch (UnexpectedException e) {
			caughtUnexpectedException = true;
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();

		Assert.assertTrue(caughtUnexpectedException);

		Assert.assertTrue("Framework stopped too early", elapsedTimeMs >= TASK_DURATION_MS);
		Assert.assertTrue("Framework stopped too late", elapsedTimeMs <= TASK_DURATION_MS + PRECISION_MS);
	}

	/**
	 * This test demonstrates how to protecting against the time loss due to unexpected exceptions
	 * by guarding the code inside the try-block with {@code coordinator.permitTaskSubmission()}.
	 * The disadvantage is that no task will be executed until the end of the try-block.
	 */
	@Test
	public void testUnexpectedExceptionWithImmediateStop() {
		for (boolean throwException : BOOLEANS) {
			TestUtils.waitForEmptyCommonForkJoinPool();
			StopWatch stopWatch = new StopWatch();
			boolean caughtUnexpectedException = false;
			try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
				coordinator.permitTaskSubmission(false);
				coordinator.execute(() -> Thread.sleep(TASK_DURATION_MS));
				throwUnexpectedException(throwException);
				coordinator.permitTaskSubmission(true);
			} catch (InterruptedException e) {
				Assert.fail("Interrupted exception");
			} catch (Throwable t) {
				caughtUnexpectedException = true;
			}
			long elapsedTimeMs = stopWatch.getElapsedTimeMs();

			if (throwException) {
				Assert.assertTrue("No exception has been thrown", caughtUnexpectedException);

				Assert.assertTrue("Framework stopped too late", elapsedTimeMs <= PRECISION_MS);
			} else {
				Assert.assertFalse("An exception has been thrown", caughtUnexpectedException);

				Assert.assertTrue("Framework stopped too early", elapsedTimeMs >= TASK_DURATION_MS);
				Assert.assertTrue("Framework stopped too late", elapsedTimeMs <= TASK_DURATION_MS + PRECISION_MS);
			}
		}
	}

	private void throwUnexpectedException(boolean throwException) {
		if (throwException) {
			throw new UnexpectedException();
		}
	}

	private static class UnexpectedException extends RuntimeException {}
}
