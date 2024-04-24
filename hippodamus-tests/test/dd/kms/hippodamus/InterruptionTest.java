package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This test verifies that Hippodamus behaves correctly when the
 * coordinator thread is interrupted.
 */
public class InterruptionTest
{
	private static final long	PRECISION_MS				= 200;
	private static final int	NUM_SUB_TASKS				= Runtime.getRuntime().availableProcessors();
	private static final long	TEST_TIME_MS				= 10000;
	private static final long	SUB_TASK_TIME_MS 			= 500;
	private static final long	MAIN_TASK_START_DELAY_MS	= 200;
	private static final long	MAIN_TASK_FINISH_DELAY_MS	= 2 * PRECISION_MS;

	@Test
	void testInterruption() {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Random random = new Random(248373824L);
		long endTimeMs = System.currentTimeMillis() + TEST_TIME_MS;
		while (System.currentTimeMillis() < endTimeMs) {
			long waitTimeMs = random.nextInt((int) (MAIN_TASK_START_DELAY_MS + SUB_TASK_TIME_MS + MAIN_TASK_FINISH_DELAY_MS / 2));
			TaskState taskState = new TaskState();
			Future<?> task = executorService.submit(() -> mainTask(taskState));

			try {
				// wait less time than the main task would need to run to completion
				Thread.sleep(waitTimeMs);
			} catch (InterruptedException e) {
				Assertions.fail("Test has been interrupted unexpectedly");
			}

			long cancelTimeMs = System.currentTimeMillis();
			task.cancel(true);

			/*
			 * Wait until the main task has finished. Unfortunately, we cannot
			 * use task.get() because it simply throws a CancellationException.
			 */
			synchronized (taskState) {
				while (taskState.endTimeMs < 0) {
					try {
						taskState.wait();
					} catch (InterruptedException e) {
						Assertions.fail("Test task has been interrupted");
					}
				}
			}

			long interruptionDelayMs = taskState.endTimeMs - cancelTimeMs;

			Assertions.assertNull(taskState.throwable, "Unexpected exception in main task: " + taskState.throwable);
			Assertions.assertFalse(taskState.completed, "Main task should have been interrupted, but has completed");
			Assertions.assertTrue(interruptionDelayMs >= 0, "Main task has been interrupted before the test actually interrupted it");
			Assertions.assertTrue(interruptionDelayMs <= PRECISION_MS, "Main task took too long until interruption: " + interruptionDelayMs + " ms");
		}
	}

	/**
	 * Waits some time, then starts some sub tasks and waits for them to complete, and
	 * then again waits some time.<br>
	 * Additionally, it fills {@code taskState} with information that is evaluated by
	 * the test thread.
	 */
	private void mainTask(TaskState taskState) {
		taskState.startTimeMs = System.currentTimeMillis();

		try {
			Thread.sleep(MAIN_TASK_START_DELAY_MS);
			try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
				for (int i = 0; i < NUM_SUB_TASKS; i++) {
					coordinator.execute(() -> subTask(SUB_TASK_TIME_MS));
				}
			}
			Thread.sleep(MAIN_TASK_FINISH_DELAY_MS);
			taskState.completed = true;
		} catch (InterruptedException e) {
			// expected
			taskState.completed = false;
		} catch (Throwable t) {
			taskState.completed = false;
			taskState.throwable = t;
		}
		synchronized (taskState) {
			taskState.endTimeMs = System.currentTimeMillis();
			// inform test thread that the main task has finished
			taskState.notifyAll();
		}
	}

	/**
	 * This subtask simply waits for the specified amount of time, but
	 * terminates earlier if interrupted.
	 */
	private void subTask(long timeMs) {
		if (timeMs == 0) {
			return;
		}
		long endTimeMs = System.currentTimeMillis() + timeMs;
		while (System.currentTimeMillis() < endTimeMs) {
			if (Thread.currentThread().isInterrupted()) {
				break;
			}
		}
	}

	private static class TaskState
	{
		long startTimeMs	= -1;
		long endTimeMs		= -1;
		Throwable throwable	= null;
		boolean completed	= false;
	}
}
