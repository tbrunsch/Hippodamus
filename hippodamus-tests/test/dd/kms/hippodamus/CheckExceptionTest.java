package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.coordinator.TestExecutionCoordinator;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * This test checks whether the method {@link ExecutionCoordinator#checkException()} throws an exception if exists.
 */
class CheckExceptionTest
{
	private static final long	TIME_UNTIL_EXCEPTION_MS	= 500;
	private static final long	SLEEP_TIME_MS			= 100;
	private static final long	PRECISION_MS			= 100;

	@Test
	void testCheckException() {
		TestUtils.waitForEmptyCommonForkJoinPool();

		boolean caughtException = false;
		TestEventManager eventManager = new TestEventManager();
		try (TestExecutionCoordinator coordinator = TestUtils.wrap(Coordinators.createExecutionCoordinator(), eventManager)) {
			coordinator.execute(() -> {
				TestUtils.simulateWork(TIME_UNTIL_EXCEPTION_MS);
				throw new TestException();
			});
			while (eventManager.getElapsedTimeMs() <= 2*TIME_UNTIL_EXCEPTION_MS) {
				TestUtils.simulateWork(SLEEP_TIME_MS);
				try {
					coordinator.checkException();
				} catch (Throwable e) {
					Assertions.assertTrue(e instanceof TestException, "Caught unexpected exception " + e);
					caughtException = true;
					break;
				}
			}
		} catch (TestException e) {
			// happens if checkException() does not throw an exception => test will fail
		}
		Assertions.assertTrue(caughtException, "An exception has been swallowed");
		CoordinatorEvent closedEvent = new CoordinatorEvent(CoordinatorState.CLOSED);

		TestUtils.assertTimeBounds(TIME_UNTIL_EXCEPTION_MS, SLEEP_TIME_MS + PRECISION_MS, eventManager.getElapsedTimeMs(closedEvent));
	}
}
