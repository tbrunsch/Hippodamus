package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * This test checks whether the method {@link ExecutionCoordinator#checkException()}
 * really throws an exception if exists.
 */
class CheckExceptionTest
{
	private static final String	EXCEPTION_MESSAGE	= "Break infinite loop!";

	@Test
	void testCheckException() {
		boolean caughtException = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> {
				TestUtils.simulateWork(500);
				throw new TestException(EXCEPTION_MESSAGE);
			});
			while (true) {
				coordinator.checkException();
			}
		} catch (TestException e) {
			Assertions.assertEquals(EXCEPTION_MESSAGE, e.getMessage(), "Wrong exception message");
			caughtException = true;
		}
		if (!caughtException) {
			Assertions.fail("An exception has been swallowed");
		}
	}

	private static class TestException extends Exception
	{
		TestException(String message) {
			super(message);
		}
	}
}
