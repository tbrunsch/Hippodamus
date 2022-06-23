package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ListenerTest
{
	private static final int	VALUE				= 42;

	private static final String	ID_COORDINATOR		= "coordinator";
	private static final String	ID_TASK				= "task";

	@Test
	void testExceptionAndCompletionListenerStillRunning() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<Integer> result = coordinator.execute(() -> {
				int value = getValue(VALUE, 1000, false);
				if (Thread.currentThread().isInterrupted()) {
					throw new InterruptedException();
				}
				return value;
			});
			coordinator.execute(() -> getValue(VALUE, 0, true));	// exception
			TestUtils.simulateWork(500);
			/*
			 * - exception should already be thrown => will be thrown in close(), before task finishes
			 * - task should still be running when completion listener is added
			 * => task is stopped
			 * => task throws an InterruptedException which is not considered anymore
			 * => completion listener will not be notified
			 */
			result.onCompletion(() -> {
				Assertions.assertEquals(VALUE, (int) result.get(), "Result handle has wrong value");
				orderVerifier.register(ID_TASK);
			});
		} catch (TestException e) {
			orderVerifier.register(ID_COORDINATOR);
		} catch (InterruptedException e) {
			Assertions.fail("Unexpected exception: " + e);
		}
		orderVerifier.checkIdOrder(Collections.singletonList(ID_COORDINATOR));
	}

	@Test
	void testExceptionAndCompletionListenerAlreadyFinished() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<Integer> result = coordinator.execute(() -> {
				int value = getValue(VALUE, 500, false);
				if(Thread.interrupted()) {
					throw new InterruptedException();
				}
				return value;
			});
			coordinator.execute(() -> getValue(VALUE, 0, true));	// exception
			TestUtils.simulateWork(1000);
			/*
			 * exception is throw before task can complete
			 * => task is stopped
			 * => task throws an InterruptedException which is not considered anymore
			 * => completion listener will not be notified
			 */
			result.onCompletion(() -> {
				Assertions.assertEquals(VALUE, (int) result.get(), "Result handle has wrong value");
				orderVerifier.register(ID_TASK);
			});
		} catch (TestException e) {
			orderVerifier.register(ID_COORDINATOR);
		} catch (InterruptedException e) {
			// TestException is thrown earlier and will be preferred
			Assertions.fail("Unexpected exception: " + e);
		}
		orderVerifier.checkIdOrder(Collections.singletonList(ID_COORDINATOR));
	}

	private int getValue(int result, long waitTimeMs, boolean throwException) {
		TestUtils.simulateWork(waitTimeMs);
		if (throwException) {
			throw new TestException();
		}
		return result;
	}

	private static class OrderVerifier
	{
		private final List<String> ids	= new ArrayList<>();
		synchronized void register(String id) {
			ids.add(id);
		}

		void checkIdOrder(List<String> expected) {
			Assertions.assertEquals(expected, ids, "Wrong order of ids");
		}

	}

	private static class TestException extends RuntimeException {}
}
