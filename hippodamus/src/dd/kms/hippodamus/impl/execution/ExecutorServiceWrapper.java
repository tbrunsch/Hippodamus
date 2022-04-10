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
		return create(ForkJoinPool.commonPool(), false, maxParallelism);
	}

	public static ExecutorServiceWrapper create(int numThreads, int maxParallelism) {
		return create(Executors.newWorkStealingPool(numThreads), true, maxParallelism);
	}

	public static ExecutorServiceWrapper create(ExecutorService executorService, boolean shutdownRequired, int maxParallelism) {
		return new ExecutorServiceWrapper(executorService, shutdownRequired, maxParallelism);
	}

	private final ExecutorService				executorService;
	private final boolean						shutdownRequired;
	private final int							maxParallelism;

	/*
	 * The unsubmitted tasks are ordered according to their id. The reason is that tasks with a lower id cannot depend
	 * on tasks with higher ids because the ids reflect the tasks' creation order. So this order is save even if one
	 * forgets to specify certain dependencies.
	 */
	private final Queue<InternalTaskHandleImpl>	unsubmittedTasks 			= new PriorityQueue<>(Comparator.comparingInt(InternalTaskHandleImpl::getId));

	/**
	 * Number of tasks that have been submitted to the wrapped {@link ExecutorService} and
	 * that have not finished yet.
	 */
	private int 								numPendingSubmittedTasks;

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
	public InternalTaskHandleImpl submit(int id, Runnable runnable) {
		InternalTaskHandleImpl taskHandle = new InternalTaskHandleImpl(id, () -> submitNow(runnable));
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
			InternalTaskHandleImpl taskHandle = unsubmittedTasks.poll();
			if (taskHandle != null) {
				taskHandle.submit();
			}
		}
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
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
