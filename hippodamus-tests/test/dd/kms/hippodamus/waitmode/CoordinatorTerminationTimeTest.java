package dd.kms.hippodamus.waitmode;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.coordinator.TestExecutionCoordinator;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * This class tests the behavior of the {@link ExecutionCoordinator}. The test executes equally sized tasks on
 * {@link #PARALLELISM} threads. In round 0, the tasks {@code 0, ..., PARALLELISM-1} are executed, in round 1 the tasks
 * {@code PARALLELISM, ..., 2*PARALLELISM-1} and so on. After {@link #NUM_ROUNDS_UNTIL_EXCEPTION} rounds, there will be
 * a task that throws an exception after {@link #TASK_TIME_UNTIL_EXCEPTION_MS} milliseconds causing the coordinator to
 * stop all tasks. Since none of the tasks checks whether it has been stopped, all tasks of that round run as if no
 * exception had occurred. However, the coordinator will prevent further tasks from being submitted to the underlying
 * {@link ExecutorService}. Hence, the coordinator will terminate after {@code (NUM_ROUNDS_UNTIL_EXCEPTION+1)*TASK_TIME_MS}
 * milliseconds.
 * @implNote This test does not rely on the tasks being executed in submission order. Instead, it enumerates the tasks
 * according to their execution order.
 */
class CoordinatorTerminationTimeTest
{
	private static final int	PARALLELISM						= 3;
	private static final long	TASK_TIME_MS					= 1000;
	private static final long	TASK_TIME_UNTIL_EXCEPTION_MS	= TASK_TIME_MS / 2;
	private static final int	NUM_ROUNDS_UNTIL_EXCEPTION		= 2;
	private static final int	NUM_TASKS_UNTIL_EXCEPTION		= NUM_ROUNDS_UNTIL_EXCEPTION*PARALLELISM;
	private static final int	NUM_TASKS						= NUM_TASKS_UNTIL_EXCEPTION	+ 2*PARALLELISM;

	private static final long	PRECISION_MS					= 300;

	@Test
	void testCoordinatorTerminationTime() {
		Assumptions.assumeTrue(TestUtils.getDefaultParallelism() >= PARALLELISM, "Insufficient number of processors for this test");

		TaskCounter counter = new TaskCounter();
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.COMPUTATIONAL, PARALLELISM);
		TestEventManager eventManager = new TestEventManager();
		boolean caughtException = false;
		List<Handle> tasks = new ArrayList<>();
		try (TestExecutionCoordinator coordinator = TestUtils.wrap(builder.build(), eventManager)) {
			for (int i = 0; i < NUM_TASKS; i++) {
				Handle task = coordinator.execute(() -> run(counter));
				tasks.add(task);
			}
		} catch (TestException e) {
			caughtException = true;
		}

		Assertions.assertTrue(caughtException, "An exception has been swallowed");

		List<Handle> exceptionalTasks = tasks.stream()
			.filter(task -> eventManager.encounteredEvent(task, HandleState.TERMINATED_EXCEPTIONALLY))
			.collect(Collectors.toList());

		Assertions.assertEquals(1, exceptionalTasks.size(), "Exactly one task should have thrown an exception");
		Handle exceptionalTask = exceptionalTasks.get(0);

		long exceptionTimeMs = eventManager.getElapsedTimeMs(exceptionalTask, HandleState.TERMINATED_EXCEPTIONALLY);
		long expectedExceptionTimeMs = NUM_ROUNDS_UNTIL_EXCEPTION * TASK_TIME_MS + TASK_TIME_UNTIL_EXCEPTION_MS;
		TestUtils.assertTimeBounds(expectedExceptionTimeMs, PRECISION_MS, exceptionTimeMs, "Throwing an exception");

		int expectedNumberOfRounds = NUM_ROUNDS_UNTIL_EXCEPTION + 1;

		List<Handle> startedTasks = tasks.stream()
			.filter(task -> eventManager.encounteredEvent(task, HandleState.STARTED))
			.collect(Collectors.toList());
		int expectedNumberOfStartedTasks = expectedNumberOfRounds * PARALLELISM;
		Assertions.assertEquals(expectedNumberOfStartedTasks, startedTasks.size(), "Wrong number of started tasks");

		long coordinatorTimeMs = eventManager.getElapsedTimeMs(new CoordinatorEvent(CoordinatorState.CLOSED));
		long expectedCoordinatorTimeMs = expectedNumberOfRounds * TASK_TIME_MS;
		TestUtils.assertTimeBounds(expectedCoordinatorTimeMs, PRECISION_MS, coordinatorTimeMs);
	}

	private void run(TaskCounter counter) throws TestException {
		int id = counter.getAndIncrement();
		if (id == NUM_TASKS_UNTIL_EXCEPTION) {
			TestUtils.simulateWork(TASK_TIME_UNTIL_EXCEPTION_MS);
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
