package dd.kms.hippodamus;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListenerTest
{
	private static final int	VALUE				= 42;

	private static final String	ID_COORDINATOR		= "coordinator";
	private static final String	ID_TASK				= "task";

	@Test
	public void testCompletionListenerStillRunning() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<Integer> result = coordinator.execute(() -> getValue(VALUE, 500, false));
			// task should still be running when completion listener is added
			result.onCompletion(() -> {
				Assert.assertEquals("Result handle has wrong value", VALUE, (int) result.get());
				orderVerifier.register(ID_TASK);
			});
		}
		orderVerifier.register(ID_COORDINATOR);
		orderVerifier.checkIdOrder(Arrays.asList(ID_TASK, ID_COORDINATOR));
	}

	@Test
	public void testCompletionListenerAlreadyFinished() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<Integer> result = coordinator.execute(() -> getValue(VALUE, 0, false));
			TestUtils.simulateWork(500);
			// task should already have completed when completion listener is added
			result.onCompletion(() -> {
				Assert.assertEquals("Result handle has wrong value", VALUE, (int) result.get());
				orderVerifier.register(ID_TASK);
			});
		}
		orderVerifier.register(ID_COORDINATOR);
		orderVerifier.checkIdOrder(Arrays.asList(ID_TASK, ID_COORDINATOR));
	}

	@Test
	public void testExceptionListenerStillRunning() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			Handle handle = coordinator.execute(() -> getValue(VALUE, 500, true));	// exception
			// task should still be running when exception listener is added
			handle.onException(() -> orderVerifier.register(ID_TASK));
		} catch (TestException e) {
			orderVerifier.register(ID_COORDINATOR);
		}
		orderVerifier.checkIdOrder(Arrays.asList(ID_TASK, ID_COORDINATOR));
	}

	@Test
	public void testExceptionListenerAlreadyFinished() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			Handle handle = coordinator.execute(() -> getValue(VALUE, 0, true));	// exception
			TestUtils.simulateWork(500);
			// task should already have thrown an exception when exception listener is added
			handle.onException(() -> orderVerifier.register(ID_TASK));
		} catch (TestException e) {
			orderVerifier.register(ID_COORDINATOR);
		}
		orderVerifier.checkIdOrder(Arrays.asList(ID_TASK, ID_COORDINATOR));
	}

	@Test
	public void testExceptionAndCompletionListenerStillRunning() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<Integer> result = coordinator.execute(() -> getValue(VALUE, 1000, false));
			coordinator.execute(() -> getValue(VALUE, 0, true));	// exception
			TestUtils.simulateWork(500);
			/*
			 * - exception should already be thrown => will be thrown in close, before task finishes
			 * - task should still be running when completion listener is added
			 */
			result.onCompletion(() -> {
				Assert.assertEquals("Result handle has wrong value", VALUE, (int) result.get());
				orderVerifier.register(ID_TASK);
			});
		} catch (TestException e) {
			orderVerifier.register(ID_COORDINATOR);
		}
		orderVerifier.checkIdOrder(Collections.singletonList(ID_COORDINATOR));
	}

	@Test
	public void testExceptionAndCompletionListenerAlreadyFinished() {
		OrderVerifier orderVerifier = new OrderVerifier();
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<Integer> result = coordinator.execute(() -> getValue(VALUE, 500, false));
			coordinator.execute(() -> getValue(VALUE, 0, true));	// exception
			TestUtils.simulateWork(1000);
			/*
			 * - exception should already be thrown => will be thrown in close
			 * - should already have completed when completion listener is added => listener will be called before close
			 */
			result.onCompletion(() -> {
				Assert.assertEquals("Result handle has wrong value", VALUE, (int) result.get());
				orderVerifier.register(ID_TASK);
			});
		} catch (TestException e) {
			orderVerifier.register(ID_COORDINATOR);
		}
		orderVerifier.checkIdOrder(Arrays.asList(ID_TASK, ID_COORDINATOR));
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
			Assert.assertEquals("Wrong order of ids", expected, ids);
		}

	}

	private static class TestException extends RuntimeException {}
}
