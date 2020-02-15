package dd.kms.hippodamus.execution;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;

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

	private final Deque<InternalTaskHandle> unsubmittedTasks = new ArrayDeque<>();

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
	public InternalTaskHandle submit(Callable<?> callable) {
		InternalTaskHandleImpl taskHandle = new InternalTaskHandleImpl(() -> submitNow(callable));
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

	private Future<?> submitNow(Callable<?> callable) {
		numPendingSubmittedTasks++;
		// TODO: How to handle RejectedExecutionException?
		return executorService.submit(callable);
	}

	@Override
	public synchronized void close() {
		if (shutdownRequired) {
			executorService.shutdownNow();
		}
	}
}
