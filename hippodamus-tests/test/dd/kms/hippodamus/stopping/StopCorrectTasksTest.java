package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Stopping works by setting the interrupted flag of the thread the task is executed by. Since this thread is
 * reused for other tasks, particularly for tasks that are not managed by the {@link ExecutionCoordinator}, it
 * is important that the interrupted flag is cleared when a task terminates.
 */
class StopCorrectTasksTest
{
	private static final int	NUM_TASKS								= 20;
	private static final int	TASK_TIME_MS							= 3000;
	private static final int	WAIT_TIME_UNTIL_NEXT_TASK_SUBMISSION_MS	= 100;

	private final Map<Integer, Long>	startTimesById		= new ConcurrentHashMap<>();
	private final Set<Integer>			completedTaskIds	= ConcurrentHashMap.newKeySet();
	private final Set<Integer>			stoppedTaskIds		= ConcurrentHashMap.newKeySet();

	@Test
	void testStopCorrectTasks() throws ExecutionException, InterruptedException {
		List<Future<?>> additionalTasks = new ArrayList<>();

		ExecutorService executorService = Executors.newFixedThreadPool(TestUtils.getPotentialParallelism());
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.COMPUTATIONAL, executorService, false);
		long stopTime;
		try (ExecutionCoordinator coordinator = builder.build()) {
			for (int i = 1; i <= NUM_TASKS; i++) {
				int taskId = i;
				if (isTaskManagedByCoordinator(taskId)) {
					coordinator.execute(() -> run(taskId));
				} else {
					Future<?> future = executorService.submit(() -> run(taskId));
					additionalTasks.add(future);
				}
				try {
					Thread.sleep(WAIT_TIME_UNTIL_NEXT_TASK_SUBMISSION_MS);
				} catch (InterruptedException e) {
					Assertions.fail(e);
				}
			}
			stopTime = System.currentTimeMillis();
			coordinator.stop();
		}

		for (Future<?> future : additionalTasks) {
			try {
				future.get();
			} catch (CancellationException e) {
				// ok for some tasks
			}
		}

		for (int taskId = 1; taskId <= NUM_TASKS; taskId++) {
			boolean taskCompleted = completedTaskIds.contains(taskId);
			boolean taskStopped = stoppedTaskIds.contains(taskId);

			if (taskCompleted && taskStopped) {
				throw new IllegalStateException("Error in test: A task cannot have completed and stopped");
			}

			if (!startTimesById.containsKey(taskId)) {
				if (taskCompleted || taskStopped) {
					throw new IllegalStateException("Error in test: A task that has not been started cannot have completed or stopped");
				}
				continue;
			}

			long taskStartTime = startTimesById.get(taskId);

			if (isTaskManagedByCoordinator(taskId)) {
				Assertions.assertTrue(taskStartTime <= stopTime, "The task has been started after the coordinator had been stopped");
				Assertions.assertTrue(taskStopped, "The task should have been stopped");
			} else {
				Assertions.assertFalse(taskStopped, "Foreign tasks should not be stopped");
			}
		}
	}

	private void run(int taskId) {
		long startTime = System.currentTimeMillis();
		startTimesById.put(taskId, startTime);
		Thread thread = Thread.currentThread();
		long endTime = startTime + TASK_TIME_MS;
		while (System.currentTimeMillis() < endTime) {
			if (thread.isInterrupted()) {
				onTaskStopped(taskId);
				return;
			}
		}
		onTaskCompleted(taskId);
	}

	private void onTaskCompleted(int taskId) {
		completedTaskIds.add(taskId);
	}

	private void onTaskStopped(int taskId) {
		stoppedTaskIds.add(taskId);
	}

	private boolean isTaskManagedByCoordinator(int taskId) {
		// manage tasks with even id
		return taskId % 2 == 0;
	}
}
