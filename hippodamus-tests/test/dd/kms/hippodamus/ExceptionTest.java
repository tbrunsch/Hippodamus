package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

/**
 * This test focuses on how the framework handles exceptions.<br>
 * <br>
 * The framework should force the user to write catch clauses for exactly the checked exceptions that are declared to be
 * thrown by the methods called asynchronously.
 */
class ExceptionTest
{
	private static final long	TASK_TIME_1_MS	= 1000;
	private static final long	TASK_TIME_2_MS	= 500;

	static {
		assert TASK_TIME_2_MS <= TASK_TIME_1_MS - 300 : "The test assumes that task 2 terminates significantly earlier than task 1";
	}

	@ParameterizedTest(name = "exception in tasks: {0}, {1}")
	@MethodSource("getParameters")
	void testExceptions(boolean throwExceptionInTask1, boolean throwExceptionInTask2) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> task1(throwExceptionInTask1));
			coordinator.execute(() -> task2(throwExceptionInTask2));
		} catch (TestException exception1) {
			Assertions.assertTrue(throwExceptionInTask1, "Unexpected exception in task 1");
			Assertions.assertFalse(throwExceptionInTask2, "Exception from task 2 should have been thrown instead");
			return;
		} catch (TestException2 exception2) {
			Assertions.assertTrue(throwExceptionInTask2, "Unexpected exception in task 2");
			return;
		}
		Assertions.assertTrue(!throwExceptionInTask1 && !throwExceptionInTask2, "An exception has been swallowed");
	}

	private void task1(boolean throwException) throws TestException {
		TestUtils.simulateWork(TASK_TIME_1_MS);
		if (throwException) {
			throw new TestException();
		}
	}

	private void task2(boolean throwException) throws TestException2 {
		TestUtils.simulateWork(TASK_TIME_2_MS);
		if (throwException) {
			throw new TestException2();
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

	private static class TestException2 extends Exception {}
}
