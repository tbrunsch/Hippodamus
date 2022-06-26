package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

/**
 * This test checks that exceptions in listeners are handled correctly. Since we consider such exceptions as internal
 * errors, these exceptions are ranked higher than, e.g., exceptions that are thrown within the task and are therefore
 * thrown as {@link CoordinatorException}s.
 */
class ListenerExceptionTest
{
	private static final long	WAIT_TIME_MS				= 500;

	private static final String	TASK_EXCEPTION_MESSAGE		= "This is an exception in the task";
	private static final String	LISTENER_EXCEPTION_MESSAGE	= "This is an exception in the listener";

	@ParameterizedTest(name = "task terminates exceptionally: {0}, add listener before termination: {1}")
	@MethodSource("getParameters")
	void testExceptionInListener(boolean taskTerminatesExceptionally, boolean addListenerBeforeTermination) {
		long taskTimeMs = addListenerBeforeTermination ? WAIT_TIME_MS : 0;
		long timeUntilListenerRegistrationMs = addListenerBeforeTermination ? 0 : WAIT_TIME_MS;

		Runnable listener = () -> {
			throw new RuntimeException(LISTENER_EXCEPTION_MESSAGE);
		};

		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			Handle handle = coordinator.execute(() -> {
				TestUtils.simulateWork(taskTimeMs);
				if (taskTerminatesExceptionally) {
					// to ensure that exception listener is called
					throw new RuntimeException(TASK_EXCEPTION_MESSAGE);
				}
			});
			TestUtils.simulateWork(timeUntilListenerRegistrationMs);

			if (taskTerminatesExceptionally) {
				handle.onException(listener);
			} else {
				handle.onCompletion(listener);
			}
		} catch (CoordinatorException e) {
			String message = e.getMessage();
			Assertions.assertTrue(message.contains(LISTENER_EXCEPTION_MESSAGE), "The exception message did not contain the listener exception text: " + message);
			return;
		}
		Assertions.fail("An exception has been swallowed");
	}

	static List<Object[]> getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (boolean taskTerminatesExceptionally : TestUtils.BOOLEANS) {
			for (boolean addListenerBeforeTermination : TestUtils.BOOLEANS) {
				parameters.add(new Object[]{taskTerminatesExceptionally, addListenerBeforeTermination});
			}
		}
		return parameters;
	}
}
