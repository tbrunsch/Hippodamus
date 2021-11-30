package dd.kms.hippodamus.impl.execution;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class ExecutorServiceWrapper implements AutoCloseable
{
	public static ExecutorServiceWrapper commonForkJoinPoolWrapper(int maxParallelism) {
		return new ExecutorServiceWrapper(ForkJoinPool.commonPool(), false, maxParallelism);
	}

	public static ExecutorServiceWrapper create(int numThreads, int maxParallelism) {
		return new ExecutorServiceWrapper(Executors.newWorkStealingPool(numThreads), true, maxParallelism);
	}

	public static ExecutorServiceWrapper create(ExecutorService executorService, boolean shutdownRequired, int maxParallelism) {
		return new ExecutorServiceWrapper(executorService, shutdownRequired, maxParallelism);
	}

	private final ExecutorService			executorService;
	private final boolean					shutdownRequired;
	private final int						maxParallelism;

	private final Queue<InternalTaskHandle> unsubmittedTasks 			= new PriorityQueue<>(Comparator.comparingInt(InternalTaskHandle::getId));

	private int 							numPendingSubmittedTasks;

	private ExecutorServiceWrapper(ExecutorService executorService, boolean shutdownRequired, int maxParallelism) {
		this.executorService = executorService;
		this.shutdownRequired = shutdownRequired;
		this.maxParallelism = maxParallelism;
	}

	public ExecutorServiceWrapper derive(ExecutorService executorService, boolean shutdownRequired) {
		return create(executorService, shutdownRequired, this.maxParallelism);
	}

	public ExecutorServiceWrapper derive(int maxParallelism) {
		return create(this.executorService, this.shutdownRequired, maxParallelism);
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	public InternalTaskHandle submit(int id, Runnable runnable) {
		InternalTaskHandle taskHandle = new InternalTaskHandleImpl(id, () -> submitNow(runnable));
		if (canSubmitTask()) {
			taskHandle.submit();
		} else {
			unsubmittedTasks.add(taskHandle);
		}
		return taskHandle;
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	public void onTaskCompleted() {
		numPendingSubmittedTasks--;
		if (canSubmitTask()) {
			InternalTaskHandle taskHandle = unsubmittedTasks.poll();
			if (taskHandle != null) {
				taskHandle.submit();
			}
		}
	}

	private boolean canSubmitTask() {
		return numPendingSubmittedTasks < maxParallelism;
	}

	private Future<?> submitNow(Runnable runnable) {
		numPendingSubmittedTasks++;
		return executorService.submit(runnable);
	}

	@Override
	public synchronized void close() {
		if (shutdownRequired) {
			executorService.shutdownNow();
		}
	}
}
