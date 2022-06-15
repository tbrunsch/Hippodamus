package dd.kms.hippodamus.parallelism;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.coordinator.TestExecutionCoordinator;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This test ensures that no deadlocks occur just because tasks are waiting on each other and block some of the limited
 * number of threads. The setup is as follows: There are four tasks. Each task depends on its predecessor, but only
 * task 2 explicitly specifies this dependency. The test is executed for 1 up to 3 threads. The expected behavior
 * depending on this number is described in the remainder.
 * <ul>
 *     <li>
 *         With 1 thread: All tasks are executed sequentially.
 *     </li>
 *     <li>
 *         With 2 threads:<br>
 *         Thread 1: Task 1 | Task 2 | ---- Task 4 ----|<br>
 *         Thread 2: -------- Task 3 ---------|
 *     </li>
 *     <li>
 *         With 3 threads:<br>
 *         Thread 1: Task 1 | Task 2 |<br>
 *         Thread 2: -------- Task 3 ---------|<br>
 *         Thread 3: ------------- Task 4 -------------|<br>
 *     </li>
 * </ul>
 * These schedules hold if the maximum parallelism specified for the coordinator is at most the number of available
 * threads. Deadlocks can occur if the maximum parallelism is larger than the actual number of threads. Consider, e.g.,
 * the case that the maximum parallelism is 2, but the actual number of threads is only 1. The coordinator will then
 * create the following schedule:<br>
 * Thread 1: Task 1|Task 3|Task2<br>
 * Since task 3 is waiting for task 2, which will not start until task 3 has finished, we are in a deadlock.<br>
 * <br>
 * Note that Hippodamus would prevent deadlocks if all dependencies are specified or if no dependencies are specified at
 * all (provided the submission order of the tasks is a feasible sequential schedule).
 */
class NoDeadlockTest
{
	private static final long TASK_TIME_MS = 500;
	private static final long	PRECISION_MS		= 200;

	@ParameterizedTest(name = "num threads: {0}, max parallelism: {1}")
	@MethodSource("getParameters")
	void testNoDeadlock(int numThreads, int maxParallelism) {
		long expectedRuntimeMs = 4* TASK_TIME_MS;	// all tasks must effectively be processed sequentially

		ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.COMPUTATIONAL, executorService, true)
			.maximumParallelism(TaskType.COMPUTATIONAL, maxParallelism);
		TestEventManager eventManager = new TestEventManager();
		List<ResultHandle<Integer>> tasks = new ArrayList<>();
		try (TestExecutionCoordinator coordinator = TestUtils.wrap(builder.build(), eventManager)) {
			ResultHandle<Integer> task1 = coordinator.execute(() -> returnWithDelay(1));
			ResultHandle<Integer> task2 = coordinator.configure().dependencies(task1).execute(() -> returnWithDelay(task1.get() + 1));
			ResultHandle<Integer> task3 = coordinator.execute(() -> returnWithDelay(task2.get() + 1));
			ResultHandle<Integer> task4 = coordinator.execute(() -> returnWithDelay(task3.get() + 1));

			tasks.add(task1);
			tasks.add(task2);
			tasks.add(task3);
			tasks.add(task4);

			/*
			 * We do not want tests to stuck in a deadlock, so we stop the coordinator manually after a sufficiently
			 * large amount of time. If there was a deadlock, then stopping the coordinator will lead to a
			 * CancellationException which lets the test fail.
			 */
			new Thread(() -> {
				TestUtils.simulateWork(expectedRuntimeMs + 2*PRECISION_MS);
				coordinator.stop();
			}).start();
		} catch (CancellationException e) {
			// caused by resolving a deadlock manually
			Assertions.fail("Detected a deadlock");
		}

		long[] expectedStartTimeMs = new long[4];
		expectedStartTimeMs[0] = 0;
		expectedStartTimeMs[1] = TASK_TIME_MS;
		expectedStartTimeMs[2] =	maxParallelism == 1	? 2*TASK_TIME_MS
														: 0;
		expectedStartTimeMs[3] =	maxParallelism == 1 ? 3*TASK_TIME_MS :
									maxParallelism == 2	? 2*TASK_TIME_MS
														: 0;
		for (int i = 0; i < 4; i++) {
			ResultHandle<Integer> task = tasks.get(i);
			Assertions.assertEquals(i+1, task.get(), "Wrong return value of task " + (i+1));

			long startTimeMs = eventManager.getElapsedTimeMs(task, HandleState.STARTED);
			long endTimeMs = eventManager.getElapsedTimeMs(task, HandleState.COMPLETED);
			long expectedEndTimeMs = (i+1)*TASK_TIME_MS; // end times same as in sequential schedule

			TestUtils.assertTimeBounds(expectedStartTimeMs[i], PRECISION_MS, startTimeMs, "Starting task " + (i+1));
			TestUtils.assertTimeBounds(expectedEndTimeMs, PRECISION_MS, endTimeMs, "Finishing task " + (i+1));
		}

		TestUtils.assertTimeBounds(expectedRuntimeMs, PRECISION_MS, eventManager.getElapsedTimeMs(new CoordinatorEvent(CoordinatorState.CLOSED)));
	}

	private int returnWithDelay(int value) {
		try {
			Thread.sleep(TASK_TIME_MS);
		} catch (InterruptedException e) {
			throw new CancellationException();
		}
		return value;
	}

	static Object getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (int numThreads = 1; numThreads <= 3; numThreads++) {
			// limit maximum parallelism to number of threads because otherwise deadlocks will occur
			for (int maxParallelism = 1; maxParallelism <= numThreads; maxParallelism++) {
				parameters.add(new Object[]{numThreads, maxParallelism});
			}
		}
		return parameters;
	}
}
