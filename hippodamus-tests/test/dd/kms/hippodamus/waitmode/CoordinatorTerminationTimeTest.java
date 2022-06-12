package dd.kms.hippodamus.waitmode;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * This class tests the behavior of the {@link ExecutionCoordinator}.<br>
 * <br>
 * The test executes equally sized tasks on {@link #PARALLELISM} threads. In each round, {@code NUM_THREADS} tasks
 * are executed in parallel. After {@link #NUM_ROUNDS_UNTIL_EXCEPTION} rounds, there will be a task that throws
 * an exception after {@link #HALF_TASK_TIME_MS} milliseconds causing the coordinator to stop all tasks. The
 * coordinator will let the other tasks of the current round finish their execution. The total time will therefore be ({@code NUM_ROUNDS_UNTIL_EXCEPTION}+1)*{@link #TASK_TIME_MS}.
 */
class CoordinatorTerminationTimeTest
{
	private static final int	PARALLELISM					= 3;
	private static final long	TASK_TIME_MS				= 1000;
	private static final long	HALF_TASK_TIME_MS			= TASK_TIME_MS/2;
	private static final int	NUM_ROUNDS_UNTIL_EXCEPTION	= 2;
	private static final int	NUM_TASKS_UNTIL_EXCEPTION	= NUM_ROUNDS_UNTIL_EXCEPTION*PARALLELISM;
	private static final int	NUM_TASKS					= NUM_TASKS_UNTIL_EXCEPTION	+ 2*PARALLELISM;

	private static final long	PRECISION_MS				= 300;

	@Test
	void testCoordinatorTerminationTime() {
		Assumptions.assumeTrue(TestUtils.getDefaultParallelism() >= PARALLELISM, "Insufficient number of processors for this test");

		TaskCounter counter = new TaskCounter();
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.COMPUTATIONAL, PARALLELISM);
		StopWatch stopWatch = new StopWatch();
		boolean caughtException = false;
		try (ExecutionCoordinator coordinator = builder.build()) {
			for (int i = 0; i < NUM_TASKS; i++) {
				coordinator.execute(() -> run(counter));
			}
		} catch (TestException e) {
			caughtException = true;
		}
		long coordinatorTimeMs = stopWatch.getElapsedTimeMs();
		TestUtils.waitForEmptyCommonForkJoinPool();
		long poolTimeMs = stopWatch.getElapsedTimeMs();

		Assertions.assertTrue(caughtException, "An exception has been swallowed");

		long expectedCoordinatorTimeMs = (NUM_ROUNDS_UNTIL_EXCEPTION+1)*TASK_TIME_MS;
		TestUtils.assertTimeBounds(expectedCoordinatorTimeMs, PRECISION_MS, coordinatorTimeMs);

		long expectedPoolTimeMs = (NUM_ROUNDS_UNTIL_EXCEPTION+1)*TASK_TIME_MS;
		TestUtils.assertTimeBounds(expectedPoolTimeMs, PRECISION_MS, poolTimeMs);
	}

	private void run(TaskCounter counter) throws TestException {
		int id = counter.getAndIncrement();
		if (id == NUM_TASKS_UNTIL_EXCEPTION) {
			TestUtils.simulateWork(HALF_TASK_TIME_MS);
			throw new TestException();
		}
		TestUtils.simulateWork(TASK_TIME_MS);
	}

	private static class TaskCounter
	{
		private int	counter;

		synchronized int getAndIncrement() {
			return counter++;
		}
	}

	private static class TestException extends Exception {}
}
