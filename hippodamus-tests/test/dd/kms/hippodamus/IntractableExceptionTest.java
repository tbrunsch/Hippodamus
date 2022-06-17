package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.TestEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test focuses on the behavior of the framework in case of exceptions that occur when the framework has no chance
 * to handle them.
 */
class IntractableExceptionTest
{
	private static final int	TASK_DURATION_MS	= 1000;
	private static final int	PRECISION_MS		= 300;

	/**
	 * This test demonstrates that all tasks will run to completion although an exception has been thrown inside the
	 * try-block. The reason for this is that the coordinator does not get informed about this exception and the
	 * coordinator's close method will be called before the code inside the catch-clause is executed.
	 */
	@Test
	void testIntractableException() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		TestEventManager eventManager = new TestEventManager();
		Handle task = null;
		boolean caughtIntractableException = false;
		try (ExecutionCoordinator coordinator = TestUtils.wrap(Coordinators.createExecutionCoordinator(), eventManager)) {
			task = coordinator.execute(() -> TestUtils.simulateWork(TASK_DURATION_MS));
			throwIntractableException(true);
		} catch (TestException e) {
			caughtIntractableException = true;
		}
		Assertions.assertTrue(caughtIntractableException, "An exception has been swallowed");

		long taskCompletedTimeMs = eventManager.getElapsedTimeMs(task, HandleState.COMPLETED);
		long totalRuntimeMs = eventManager.getElapsedTimeMs(new CoordinatorEvent(CoordinatorState.CLOSED));

		Assertions.assertTrue(taskCompletedTimeMs <= totalRuntimeMs);
		TestUtils.assertTimeBounds(TASK_DURATION_MS, PRECISION_MS, totalRuntimeMs);
	}

	/**
	 * This test demonstrates how to protect against the time loss due to intractable exceptions by guarding the code
	 * inside the try-block with {@link ExecutionCoordinator#permitTaskSubmission(boolean)}. The disadvantage is that no
	 * task will be executed until the end of the try-block.
	 */
	@ParameterizedTest(name = "throw exception: {0}")
	@MethodSource("getThrowExceptionValues")
	void testIntractableExceptionWithImmediateStop(boolean throwException) {
		TestUtils.waitForEmptyCommonForkJoinPool();
		TestEventManager eventManager = new TestEventManager();
		Handle task = null;
		boolean caughtIntractableException = false;
		try (ExecutionCoordinator coordinator = TestUtils.wrap(Coordinators.createExecutionCoordinator(), eventManager)) {
			coordinator.permitTaskSubmission(false);
			task = coordinator.execute(() -> TestUtils.simulateWork(TASK_DURATION_MS));
			throwIntractableException(throwException);
			eventManager.fireEvent(PermitTaskSubmissionEvent.EVENT);
			coordinator.permitTaskSubmission(true);
		} catch (TestException e) {
			caughtIntractableException = true;
		}
		long totalRuntimeMs = eventManager.getElapsedTimeMs(new CoordinatorEvent(CoordinatorState.CLOSED));

		if (throwException) {
			Assertions.assertFalse(eventManager.encounteredEvent(task, HandleState.STARTED), "The task should not have started");
			Assertions.assertTrue(caughtIntractableException, "No exception has been thrown");

			TestUtils.assertTimeUpperBound(PRECISION_MS, totalRuntimeMs);
		} else {
			Assertions.assertTrue(eventManager.before(PermitTaskSubmissionEvent.EVENT, task, HandleState.STARTED), "The task should not have started before permitted");
			Assertions.assertTrue(eventManager.encounteredEvent(task, HandleState.COMPLETED), "The task should have completed");
			Assertions.assertFalse(caughtIntractableException, "An exception has been thrown");

			TestUtils.assertTimeBounds(TASK_DURATION_MS, PRECISION_MS, totalRuntimeMs);
		}
	}

	private void throwIntractableException(boolean throwException) throws TestException {
		if (throwException) {
			throw new TestException();
		}
	}

	static Object getThrowExceptionValues() {
		return TestUtils.BOOLEANS;
	}

	private static class PermitTaskSubmissionEvent extends TestEvent
	{
		static final TestEvent	EVENT	= new PermitTaskSubmissionEvent();

		@Override
		public boolean equals(Object o) {
			return o == this;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}

		@Override
		public String toString() {
			return "Permit task submission";
		}
	}
}
