package dd.kms.hippodamus;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * This test checks whether the method {@link ExecutionCoordinator#checkException()}
 * really throws an exception if exists.
 */
public class CheckExceptionTest
{
	private static final String	EXCEPTION_MESSAGE	= "Break infinite loop!";

	@Test
	public void testCheckException() {
		boolean caughtException = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> {
				TestUtils.sleep(500);
				throw new TestException(EXCEPTION_MESSAGE);
			});
			while (true) {
				coordinator.checkException();
			}
		} catch (TestException e) {
			Assert.assertEquals("Wrong exception message", EXCEPTION_MESSAGE, e.getMessage());
			caughtException = true;
		}
		if (!caughtException) {
			Assert.fail("An exception has been swallowed");
		}
	}

	private static class TestException extends Exception
	{
		TestException(String message) {
			super(message);
		}
	}
}
