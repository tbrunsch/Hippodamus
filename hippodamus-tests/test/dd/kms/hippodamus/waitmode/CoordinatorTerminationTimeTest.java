package dd.kms.hippodamus.waitmode;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * This class tests the behavior of the {@link ExecutionCoordinator} for different values of {@link WaitMode}.<br/>
 * <br/>
 * The test executes equally sized tasks on {@link #PARALLELISM} threads. In each round, {@code NUM_THREADS} tasks
 * are executed in parallel. After {@link #NUM_ROUNDS_UNTIL_EXCEPTION} rounds, there will be a task that throws
 * an exception after {@link #HALF_TASK_TIME_MS} milliseconds causing the coordinator to stop all tasks. The
 * behavior now depends on the {@code WaitMode}:
 * <ul>
 *     <li>
 *         {@link WaitMode#UNTIL_TERMINATION}: The coordinator will let the other tasks of the current round
 *         finish their execution. The total time will therefore be ({@code NUM_ROUNDS_UNTIL_EXCEPTION}+1)*{@link #TASK_TIME_MS}.
 *     </li>
 *     <li>
 *         {@link WaitMode#UNTIL_TERMINATION_REQUESTED}: The coordinator will immediately stop and not wait
 *         for the other tasks of the current round to terminate. The total time will therefore be
 *         {@code NUM_ROUNDS_UNTIL_EXCEPTION}*{@code TASK_TIME_MS} + {@code HALF_TASK_TIME_MS}.
 *     </li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class CoordinatorTerminationTimeTest
{
	private static final int	PARALLELISM					= 3;
	private static final long	TASK_TIME_MS				= 1000;
	private static final long	HALF_TASK_TIME_MS			= TASK_TIME_MS/2;
	private static final int	NUM_ROUNDS_UNTIL_EXCEPTION	= 2;
	private static final int	NUM_TASKS_UNTIL_EXCEPTION	= NUM_ROUNDS_UNTIL_EXCEPTION*PARALLELISM;
	private static final int	NUM_TASKS					= NUM_TASKS_UNTIL_EXCEPTION	+ 2*PARALLELISM;

	private static final long	PRECISION_MS				= 300;

	@Parameterized.Parameters(name = "wait mode: {0}")
	public static Object getParameters() {
		return WaitMode.values();
	}

	private final WaitMode	waitMode;

	public CoordinatorTerminationTimeTest(WaitMode waitMode) {
		this.waitMode = waitMode;
	}

	@Test
	public void testCoordinatorTerminationTime() {
		Assume.assumeTrue("Insufficient number of processors for this test", TestUtils.getDefaultParallelism() >= PARALLELISM);

		TaskCounter counter = new TaskCounter();
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.waitMode(waitMode)
			.maximumParallelism(TaskType.REGULAR, PARALLELISM);
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

		Assert.assertTrue("An exception has been swallowed", caughtException);

		long expectedCoordinatorTimeMs = waitMode == WaitMode.UNTIL_TERMINATION
								? (NUM_ROUNDS_UNTIL_EXCEPTION+1)*TASK_TIME_MS
								: NUM_ROUNDS_UNTIL_EXCEPTION*TASK_TIME_MS + HALF_TASK_TIME_MS;
		TestUtils.assertTimeLowerBound(expectedCoordinatorTimeMs, coordinatorTimeMs);
		TestUtils.assertTimeUpperBound(expectedCoordinatorTimeMs + PRECISION_MS, coordinatorTimeMs);

		long expectedPoolTimeMs = (NUM_ROUNDS_UNTIL_EXCEPTION+1)*TASK_TIME_MS;
		TestUtils.assertTimeLowerBound(expectedPoolTimeMs, poolTimeMs);
		TestUtils.assertTimeUpperBound(expectedPoolTimeMs + PRECISION_MS, poolTimeMs);
	}

	private void run(TaskCounter counter) throws TestException {
		int id = counter.getAndIncrement();
		if (id == NUM_TASKS_UNTIL_EXCEPTION) {
			TestUtils.simulateWork(HALF_TASK_TIME_MS);
			throw new TestException();
		}
		TestUtils.simulateWork(TASK_TIME_MS);
	}

	private class TaskCounter
	{
		private int	counter;

		synchronized int getAndIncrement() {
			return counter++;
		}
	}

	private static class TestException extends Exception {};
}
