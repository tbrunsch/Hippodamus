package dd.kms.hippodamus.stopping;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import org.junit.Assert;
import org.junit.Test;

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
public class StopCorrectTasksTest
{
	private static final int	NUM_TASKS		= 100;
	private static final int	TASK_TIME_MS	= 300;

	private final Map<Integer, Handle>	handlesById			= new ConcurrentHashMap<>();
	private final Set<Integer>			completedTaskIds	= ConcurrentHashMap.newKeySet();
	private final Set<Integer>			stoppedTaskIds		= ConcurrentHashMap.newKeySet();

	@Test
	public void testStopCorrectTasks() throws ExecutionException, InterruptedException {
		List<Future<?>> additionalTasks = new ArrayList<>();

		ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.COMPUTATIONAL, forkJoinPool, false);
		try (ExecutionCoordinator coordinator = builder.build()) {
			for (int i = 1; i <= NUM_TASKS; i++) {
				int taskId = i;
				if (taskId % 2 == 0 && taskId != 2) {
					// even non-primes => start task that is not managed by the coordinator
					ForkJoinTask<?> future = forkJoinPool.submit(() -> run(taskId));
					additionalTasks.add(future);
				} else {
					// non-even numbers or 2
					Handle handle = coordinator.execute(() -> run(taskId));
					handlesById.put(taskId, handle);
				}
			}
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
			boolean taskIdIsPrime = isPrime(taskId);
			Assert.assertTrue("Task " + taskId + " should either have completed or stopped", taskCompleted ^ taskStopped);
			Assert.assertEquals("Task " + taskCompleted + " should " + (taskIdIsPrime ? "have " : "not have ") + "stopped", taskIdIsPrime, taskStopped);
		}
	}

	private void run(int taskId) {
		Thread thread = Thread.currentThread();
		onTaskStarted(taskId);
		long endTime = System.currentTimeMillis() + TASK_TIME_MS;
		while (System.currentTimeMillis() < endTime) {
			if (thread.isInterrupted()) {
				onTaskStopped(taskId);
				return;
			}
		}
		onTaskCompleted(taskId);
	}

	private void onTaskStarted(int taskId) {
		if (isPrime(taskId)) {
			stop(taskId);
		}
	}

	private void onTaskCompleted(int taskId) {
		completedTaskIds.add(taskId);
		// we must also support stopping a task after its execution
		stop(taskId);
	}

	private void onTaskStopped(int taskId) {
		stoppedTaskIds.add(taskId);
		// we must also support stopping a task after its execution
		stop(taskId);
	}

	/*
	 * Stopping a task after its execution
	 */
	private void stop(int taskId) {
		Handle handle = handlesById.get(taskId);
		if (handle != null) {
			handle.stop();
		}
	}

	private boolean isPrime(int n) {
		if (n == 1) {
			return false;
		} else if (n == 2) {
			return true;
		}
		int sqrt = (int) Math.sqrt(n);
		for (int i = 2; i <= sqrt; i++) {
			if (n % i == 0) {
				return false;
			}
		}
		return true;
	}
}

