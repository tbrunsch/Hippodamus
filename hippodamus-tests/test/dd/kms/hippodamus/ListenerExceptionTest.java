package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;

public class ListenerExceptionTest
{
	private static final String	TASK_EXCEPTION_MESSAGE		= "This is an exception in the task";
	private static final String	LISTENER_EXCEPTION_MESSAGE	= "This is an exception in the listener";

	@Test
	public void testCompletionListenerStillRunning() {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			Handle handle = coordinator.execute(() -> TestUtils.simulateWork(500));
			// task should still be running when completion listener is added
			handle.onCompletion(() -> runWithException(0, LISTENER_EXCEPTION_MESSAGE));
		} catch (Throwable t) {
			String message = t.getMessage();
			Assert.assertTrue("The exception message did not contain the listener exception text: " + message, message.contains(LISTENER_EXCEPTION_MESSAGE));
			return;
		}
		Assert.fail("An exception has been swallowed");
	}

	@Test
	public void testCompletionListenerAlreadyFinished() {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			Handle handle = coordinator.execute(() -> {});
			TestUtils.simulateWork(500);
			// task should already have completed when completion listener is added
			handle.onCompletion(() -> runWithException(0, LISTENER_EXCEPTION_MESSAGE));
		} catch (Throwable t) {
			String message = t.getMessage();
			Assert.assertTrue("The exception message did not contain the listener exception text: " + message, message.contains(LISTENER_EXCEPTION_MESSAGE));
			return;
		}
		Assert.fail("An exception has been swallowed");
	}

	@Test
	public void testExceptionListenerStillRunning() {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			Handle handle = coordinator.execute(() -> runWithException(500, TASK_EXCEPTION_MESSAGE));
			// task should still be running when exception listener is added
			handle.onException(() -> runWithException(0, LISTENER_EXCEPTION_MESSAGE));
		} catch (Throwable t) {
			// exception in listener should dominate task exception
			String message = t.getMessage();
			Assert.assertTrue("The exception message did not contain the listener exception text: " + message, message.contains(LISTENER_EXCEPTION_MESSAGE));
			return;
		}
		Assert.fail("An exception has been swallowed");
	}

	@Test
	public void testExceptionListenerAlreadyFinished() {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			Handle handle = coordinator.execute(() -> runWithException(0, TASK_EXCEPTION_MESSAGE));
			TestUtils.simulateWork(500);
			// task should already have thrown an exception when exception listener is added
			handle.onException(() -> runWithException(0, LISTENER_EXCEPTION_MESSAGE));
		} catch (Throwable t) {
			// exception in listener should dominate task exception
			String message = t.getMessage();
			Assert.assertTrue("The exception message did not contain the listener exception text: " + message, message.contains(LISTENER_EXCEPTION_MESSAGE));
			return;
		}
		Assert.fail("An exception has been swallowed");
	}

	private void runWithException(long waitTimeMs, String errorMessage) {
		TestUtils.simulateWork(waitTimeMs);
		throw new TestException(errorMessage);
	}

	private static class TestException extends RuntimeException
	{
		TestException(String message) {
			super(message);
		}
	}
}
