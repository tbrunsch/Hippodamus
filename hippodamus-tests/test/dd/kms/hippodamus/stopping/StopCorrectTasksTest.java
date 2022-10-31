package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.events.TestEvents;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Stopping works by setting the interrupted flag of the thread the task is executed by. Since this thread is reused for
 * other tasks, particularly for tasks that are not managed by the {@link ExecutionCoordinator}, it is important that
 * the interrupted flag is cleared when a task terminates. We test this by submitting several tasks to the same
 * {@link ExecutorService}. Every second task is directly sent to the {@code ExecutorService} via
 * {@link ExecutorService#submit(Runnable)}, whereas the other tasks are submitted to the coordinator, which then
 * submits them to the {@code ExecutorService}. After all tasks have been submitted (with some delay), the coordinator
 * is stopped. This should only stop the tasks handled by the coordinator.
 */
class StopCorrectTasksTest
{
	private static final int	NUM_TASKS								= 20;
	private static final int	TASK_TIME_MS							= 3000;
	private static final int	WAIT_TIME_UNTIL_NEXT_TASK_SUBMISSION_MS	= 100;

	private final Set<Integer>	stoppedTaskIds	= ConcurrentHashMap.newKeySet();

	@Test
	void testStopCorrectTasks() throws ExecutionException, InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(TestUtils.getPotentialParallelism());
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.COMPUTATIONAL, executorService, false);
		TestEventManager eventManager = new TestEventManager();
		List<Handle> managedTasks = new ArrayList<>();
		List<Future<?>> unmanagedTasks = new ArrayList<>();
		try (ExecutionCoordinator coordinator = TestUtils.wrap(builder.build(), eventManager)) {
			for (int i = 1; i <= NUM_TASKS; i++) {
				int taskId = i;
				if (isTaskManagedByCoordinator(taskId)) {
					coordinator.configure()
						.onHandleCreation(managedTasks::add)
						.execute(() -> run(taskId));
				} else {
					Future<?> future = executorService.submit(() -> run(taskId));
					unmanagedTasks.add(future);
				}
				try {
					Thread.sleep(WAIT_TIME_UNTIL_NEXT_TASK_SUBMISSION_MS);
				} catch (InterruptedException e) {
					Assertions.fail(e);
				}
			}
			coordinator.stop();
		}

		// wait for unmanaged tasks
		for (Future<?> future : unmanagedTasks) {
			try {
				future.get();
			} catch (CancellationException e) {
				Assertions.fail("Wrong task has been stopped");
			}
		}

		long stopTimeMs = eventManager.getElapsedTimeMs(TestEvents.COORDINATOR_STOPPED_EXTERNALLY);

		Iterator<Handle> managedTaskIterator = managedTasks.iterator();
		for (int taskId = 1; taskId <= NUM_TASKS; taskId++) {
			boolean taskStopped = stoppedTaskIds.contains(taskId);

			if (!isTaskManagedByCoordinator(taskId)) {
				Assertions.assertFalse(taskStopped, "Foreign tasks should not be stopped");
				continue;
			}
			Handle managedTask = managedTaskIterator.next();

			boolean taskStarted = eventManager.encounteredEvent(managedTask, HandleState.STARTED);
			boolean taskCompleted = eventManager.encounteredEvent(managedTask, HandleState.COMPLETED);

			Assertions.assertEquals(taskStarted, taskCompleted);

			if (!taskStarted) {
				Assertions.assertFalse(taskStopped);
				continue;
			}

			long taskStartTimeMs = eventManager.getElapsedTimeMs(managedTask, HandleState.STARTED);
			long taskCompletionTimeMs = eventManager.getElapsedTimeMs(managedTask, HandleState.COMPLETED);

			Assertions.assertTrue(taskStartTimeMs <= taskCompletionTimeMs);

			if (taskStopped) {
				Assertions.assertTrue(taskStartTimeMs <= stopTimeMs && stopTimeMs <= taskCompletionTimeMs);
			} else {
				Assertions.assertTrue(taskCompletionTimeMs <= stopTimeMs);
			}
		}
	}

	private void run(int taskId) {
		Thread thread = Thread.currentThread();
		long endTime = System.currentTimeMillis() + TASK_TIME_MS;
		while (System.currentTimeMillis() < endTime) {
			if (thread.isInterrupted()) {
				onTaskStopped(taskId);
				return;
			}
		}
	}

	private void onTaskStopped(int taskId) {
		stoppedTaskIds.add(taskId);
	}

	private boolean isTaskManagedByCoordinator(int taskId) {
		// manage tasks with even id
		return taskId % 2 == 0;
	}
}
