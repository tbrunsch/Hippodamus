package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This test focuses on how the framework handles exceptions.<br>
 * <br>
 * The framework should force the user to write catch clauses for exactly the checked
 * exceptions that are declared to be thrown by the methods called asynchronously.
 */
class ExceptionTest
{
	@ParameterizedTest(name = "exception in tasks: {0}, {1}")
	@MethodSource("getParameters")
	void testExceptions(boolean throwExceptionInTask1, boolean throwExceptionInTask2) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> run1(throwExceptionInTask1));
			coordinator.execute(() -> run2(throwExceptionInTask2));
		} catch (Exception1 exception1) {
			Assertions.assertTrue(throwExceptionInTask1, "Unexpected exception in task 1");
			Assertions.assertFalse(throwExceptionInTask2, "Exception from task 2 should have been thrown instead");
			return;
		} catch (Exception2 exception2) {
			Assertions.assertTrue(throwExceptionInTask2, "Unexpected exception in task 2");
			return;
		}
		Assertions.assertTrue(!throwExceptionInTask1 && !throwExceptionInTask2, "An exception has been swallowed");
	}

	private void run1(boolean throwException) throws Exception1 {
		TestUtils.simulateWork(1000);
		if (throwException) {
			throw new Exception1();
		}
	}

	private void run2(boolean throwException) throws Exception2 {
		TestUtils.simulateWork(500);
		if (throwException) {
			throw new Exception2();
		}
	}

	static List<Object> getParameters() {
		List<Object> parameters = new ArrayList<>();
		for (boolean throwExceptionInTask1 : TestUtils.BOOLEANS) {
			for (boolean throwExceptionInTask2 : TestUtils.BOOLEANS) {
				parameters.add(new Object[]{throwExceptionInTask1, throwExceptionInTask2});
			}
		}
		return parameters;
	}

	private static class Exception1 extends Exception {}

	private static class Exception2 extends Exception {}
}
