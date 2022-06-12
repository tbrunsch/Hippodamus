package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JoinTest
{
	private static final long	TASK_TIME_MS	= 500;
	private static final long	PRECISION_MS	= 300;

	@Test
	void testExceptionListenerAccessingValueOfExceptionalTask() {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<String> task = coordinator.execute(this::runExceptionally);
			/*
			 * Accessing the value of a task that terminates exceptionally causes a HandleException.
			 * Exceptional listeners cause a CoordinatorException.
			 */
			task.onException(() -> System.out.println(task.get()));
		} catch (CoordinatorException e) {
			return;
		}
		Assertions.fail("An exception has been swallowed");
	}

	@Test
	void testTaskAccessingValueOfExceptionalTask() {
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<String> task1 = coordinator.execute(this::runExceptionally);
			/*
			 * Accessing the value of a task that terminates exceptionally causes a CancellationException.
			 * This causes task 2 to terminate immediately instead of waiting for TASK_TIME_MS
			 * milliseconds.
			 *
			 * However, the coordinator should ignore the CancellationException in task 2 and prefer
			 * the TestException of task 1.
			 */
			ResultHandle<String> task2 = coordinator.execute(() -> {
				String s = task1.get();
				TestUtils.simulateWork(TASK_TIME_MS);
				return s;
			});
		} catch (TestException e) {
			long elapsedTimeMs = stopWatch.getElapsedTimeMs();
			TestUtils.assertTimeBounds(TASK_TIME_MS, PRECISION_MS, elapsedTimeMs);
			return;
		}
		Assertions.fail("An exception has been swallowed");
	}

	private String runExceptionally() {
		TestUtils.simulateWork(TASK_TIME_MS);
		throw new TestException();
	}

	private static class TestException extends RuntimeException {}
}
