package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.events.TestEvents;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test verifies that the interrupted flag is set correctly by the {@link ExecutionCoordinator} by
 * showing that by considering the interrupted flag tasks will terminate earlier than without.
 */
class StopReactionTest
{
	private static final long	TIME_UNTIL_EXCEPTION_MS	= 500;
	private static final long	TASK_2_SLEEP_INTERVAL	= 100;
	private static final int	TASK_2_SLEEP_REPETITION	= 20;
	private static final long	PRECISION_MS			= 300;

	@ParameterizedTest(name = "react to stop: {0}")
	@MethodSource("getPossibleReactToStopValues")
	void testStopWithoutStopReaction(boolean reactToStop) {
		TestUtils.waitForEmptyCommonForkJoinPool();
		boolean caughtException = false;
		TestEventManager eventManager = new TestEventManager();
		Handle task1 = null;
		Handle task2 = null;
		try (ExecutionCoordinator coordinator = TestUtils.wrap(Coordinators.createExecutionCoordinator(), eventManager)) {
			/*
			 * Task 1 throws an exception after TIME_UNTIL_EXCEPTION_MS milliseconds, which stops the coordinator, which
			 * then stops task 2.
			 */
			task1 = coordinator.execute(this::run1);
			task2 = coordinator.execute(() -> run2(reactToStop));
		} catch (TestException e) {
			caughtException = true;
		}
		Assertions.assertNotNull(task1);
		Assertions.assertNotNull(task2);

		Assertions.assertTrue(caughtException, "An exception has been swallowed");

		HandleEvent exceptionEvent = new HandleEvent(task1, HandleState.TERMINATED_EXCEPTIONALLY);
		HandleEvent task2CompletedEvent = new HandleEvent(task2, HandleState.COMPLETED);

		long exceptionTimeMs = eventManager.getElapsedTimeMs(exceptionEvent);
		TestUtils.assertTimeBounds(TIME_UNTIL_EXCEPTION_MS, PRECISION_MS, exceptionTimeMs, "Throwing exception");

		if (TestUtils.getDefaultParallelism() > 1) {
			if (reactToStop) {
				TestUtils.assertTimeBounds(0, TASK_2_SLEEP_INTERVAL + PRECISION_MS, eventManager.getDurationMs(exceptionEvent, task2CompletedEvent), "Reaction to stop");
			} else {
				TestUtils.assertTimeBounds(TASK_2_SLEEP_REPETITION * TASK_2_SLEEP_INTERVAL, PRECISION_MS, eventManager.getElapsedTimeMs(task2CompletedEvent), "Completion of task 2");
			}

			TestUtils.assertTimeBounds(0, PRECISION_MS, eventManager.getDurationMs(task2CompletedEvent, TestEvents.COORDINATOR_CLOSED), "Closing coordinator after task 2 has terminated");
		} else {
			// single thread => task 2 will not be started and coordinator closes immediately after exception in task 1
			Assertions.assertFalse(eventManager.encounteredEvent(new HandleEvent(task2, HandleState.STARTED)));
			TestUtils.assertTimeBounds(TIME_UNTIL_EXCEPTION_MS, PRECISION_MS, eventManager.getElapsedTimeMs(TestEvents.COORDINATOR_CLOSED), "Closing coordinator after task 2 has terminated");
		}
	}

	private void run1() throws TestException {
		TestUtils.simulateWork(TIME_UNTIL_EXCEPTION_MS);
		throw new TestException();
	}

	private void run2(boolean reactToStop) {
		for (int i = 0; i < TASK_2_SLEEP_REPETITION; i++) {
			if (reactToStop && Thread.currentThread().isInterrupted()) {
				return;
			}
			TestUtils.simulateWork(TASK_2_SLEEP_INTERVAL);
		}
	}

	static Object getPossibleReactToStopValues() {
		return TestUtils.BOOLEANS;
	}
}
