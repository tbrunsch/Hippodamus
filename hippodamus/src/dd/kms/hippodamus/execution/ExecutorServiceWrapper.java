package dd.kms.hippodamus.execution;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.resources.ResourceShare;

import java.util.*;
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

	/**
	 * Collection of tasks that could not be submitted yet, but are eligible for submission.
	 * No resource conflicts are known (although there might be some). The tasks are ordered
	 * according to their id.
	 */
	private final Queue<InternalTaskHandle> unsubmittedTasks 		= new PriorityQueue<>(Comparator.comparingInt(InternalTaskHandle::getId));

	private final ResourceManager			resourceManager			= new ResourceManager();

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

	public synchronized InternalTaskHandle submit(int id, Runnable runnable, List<ResourceShare<?>> requiredResourceShares, InternalCoordinator coordinator) {
		InternalTaskHandle handle = new InternalTaskHandleImpl(id, runnable, requiredResourceShares, coordinator, this::submitNow, this::onTaskCompleted);
		if (canSubmitTask()) {
			submitOrWaitForResource(handle);
		} else {
			unsubmittedTasks.add(handle);
		}
		return handle;
	}

	private void submitOrWaitForResource(InternalTaskHandle handle) {
		try {
			if (resourceManager.acquireResourceShares(handle)) {
				handle.submit();
			}
		} catch (Throwable t) {
			logInternalError(handle, "Resource manager", t);
		}
	}

	private synchronized void onTaskCompleted(InternalTaskHandle handle) {
		numPendingSubmittedTasks--;
		List<InternalTaskHandle> blockedHandles = resourceManager.releaseResourceShares(handle);
		unsubmittedTasks.addAll(blockedHandles);
		while (!unsubmittedTasks.isEmpty() && canSubmitTask()) {
			InternalTaskHandle taskHandle = unsubmittedTasks.poll();
			submitOrWaitForResource(taskHandle);
		}
	}

	private void logInternalError(InternalTaskHandle handle, String fallbackContext, Throwable internalError) {
		StackTraceElement[] stackTrace = internalError.getStackTrace();
		String context = stackTrace.length == 0 ? fallbackContext : stackTrace[0].getClassName();
		InternalCoordinator coordinator = handle.getCoordinator();
		synchronized (coordinator) {
			coordinator.logInternalException(context, internalError.getMessage(), internalError);
		}
	}

	private boolean canSubmitTask() {
		return numPendingSubmittedTasks < maxParallelism;
	}

	private Future<?> submitNow(InternalTaskHandle handle) {
		numPendingSubmittedTasks++;
		return executorService.submit(handle);
	}

	@Override
	public synchronized void close() {
		if (shutdownRequired) {
			executorService.shutdownNow();
		}
	}
}
