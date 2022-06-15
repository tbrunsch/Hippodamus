package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * This test verifies that manually stopping the coordinator works correctly.
 */
class ManualStopTest
{
	private static final long	TASK_SLEEP_INTERVAL		= 100;
	private static final int	TASK_SLEEP_REPETITION	= 20;
	private static final long	PRECISION_MS			= 500;
	private static final long	TIME_UNTIL_STOP_MS		= 1000;

	@Test
	void testManualStop() {
		Assumptions.assumeTrue(TestUtils.getDefaultParallelism() > 1, "This test requires at least two tasks to be processed in parallel");

		TestUtils.waitForEmptyCommonForkJoinPool();
		TestEventManager eventManager = new TestEventManager();
		try (ExecutionCoordinator coordinator = TestUtils.wrap(Coordinators.createExecutionCoordinator(), eventManager)) {
			coordinator.execute(this::runWithStopReaction);
			coordinator.execute(() -> {
				TestUtils.simulateWork(TIME_UNTIL_STOP_MS);
				coordinator.stop();
			});
		}

		CoordinatorEvent stoppedEvent = new CoordinatorEvent(CoordinatorState.STOPPED_EXTERNALLY);
		CoordinatorEvent closedEvent = new CoordinatorEvent(CoordinatorState.CLOSED);

		TestUtils.assertTimeBounds(TIME_UNTIL_STOP_MS, PRECISION_MS, eventManager.getElapsedTimeMs(stoppedEvent));
		TestUtils.assertTimeBounds(0, TASK_SLEEP_INTERVAL + PRECISION_MS, eventManager.getDurationMs(stoppedEvent, closedEvent));
	}

	private void runWithStopReaction() {
		for (int i = 0; i < TASK_SLEEP_REPETITION; i++) {
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			TestUtils.simulateWork(TASK_SLEEP_INTERVAL);
		}
	}
}
