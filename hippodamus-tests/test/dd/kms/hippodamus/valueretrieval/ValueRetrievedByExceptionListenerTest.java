package dd.kms.hippodamus.valueretrieval;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.ValueReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * This test focuses on the edge case in which the value of a task is accessed from within an exception listener. This
 * throws an exception in the exception listener, which is caught in the {@link ExecutionCoordinator} and there wrapped
 * within a {@link CoordinatorException} because such an access is considered an internal error.
 */
class ValueRetrievedByExceptionListenerTest
{
	private static final long	TASK_TIME_MS	= 500;
	private static final long	PRECISION_MS	= 300;

	@Test
	void testValueRetrieval() {
		ValueReference<Boolean> executedFirstExceptionListener = new ValueReference<>(false);
		ValueReference<Boolean> executedThirdExceptionListener = new ValueReference<>(false);
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<String> task = coordinator.execute(this::runExceptionally);

			task.onException(() -> executedFirstExceptionListener.set(true));

			/*
			 * Accessing the value of a task that terminates exceptionally causes a CompletionException. Exceptional
			 * listeners cause a CoordinatorException that dominates the CompletionException.
			 */
			task.onException(() -> System.out.println(task.get()));

			task.onException(() -> executedThirdExceptionListener.set(true));
		} catch (CoordinatorException e) {
			// Check that all exception listeners have been executed despite the exception in the second listener
			Assertions.assertTrue(executedFirstExceptionListener.get(), "First exception listener has not been executed");
			Assertions.assertTrue(executedThirdExceptionListener.get(), "Third exception listener has not been executed");
			return;
		} catch (TestException ignored) {
			Assertions.fail("A TestException has been thrown, but a CoordinatorException should have been thrown instead");
		}
		Assertions.fail("A CoordinatorException has been swallowed");
	}

	private String runExceptionally() throws TestException {
		TestUtils.simulateWork(TASK_TIME_MS);
		throw new TestException();
	}
}
