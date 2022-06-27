package dd.kms.hippodamus.impl.execution;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.impl.handles.HandleImpl;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * Wraps an {@link ExecutorService} to provide two additional features:
 * <ul>
 *     <li>
 *         Maximum parallelism: You can specify the maximum number of tasks the underlying {@code ExecutorService} should
 *         manage at the same time, independent of the parallelism of the {@code ExecutorService}. The {@code ExecutorServiceWrapper}
 *         will queue further tasks and submit the next of them when one of the tasks that has been submitted to the
 *         {@code ExecutorService} terminates.
 *     </li>
 *     <li>
 *         You can specify whether or not to shut down the underlying {@code ExecutorService} when the {@code ExecutorServiceWrapper}
 *         is closed, which happens when the {@link ExecutionCoordinator} is closed.
 *     </li>
 * </ul>
 */
public class ExecutorServiceWrapper implements AutoCloseable
{
	private final ExecutorService				executorService;
	private final boolean						shutdownRequired;
	private final int							maxParallelism;

	/*
	 * The unsubmitted tasks are ordered according to their id. The reason is that tasks with a lower id cannot depend
	 * on tasks with higher ids because the ids reflect the tasks' creation order. So this order is save even if one
	 * forgets to specify certain dependencies.
	 */
	private final Queue<HandleImpl<?>>			_unsubmittedTasks			= new PriorityQueue<>(Comparator.comparingInt(HandleImpl::getId));

	/**
	 * Number of tasks that have been submitted to the wrapped {@link ExecutorService} and
	 * that have not finished yet.
	 */
	private int									_numPendingSubmittedTasks;

	public ExecutorServiceWrapper(ExecutorService executorService, boolean shutdownRequired, int maxParallelism) {
		this.executorService = executorService;
		this.shutdownRequired = shutdownRequired;
		this.maxParallelism = maxParallelism;
	}

	public void _submit(HandleImpl<?> handle) {
		if (_canSubmitTask()) {
			_submitNow(handle);
		} else {
			_unsubmittedTasks.add(handle);
		}
	}

	public void _onTaskCompleted() {
		_numPendingSubmittedTasks--;
		if (_canSubmitTask()) {
			HandleImpl<?> handle = _unsubmittedTasks.poll();
			if (handle != null) {
				_submitNow(handle);
			}
		}
	}

	private boolean _canSubmitTask() {
		return _numPendingSubmittedTasks < maxParallelism;
	}

	private void _submitNow(HandleImpl<?> handle) {
		_numPendingSubmittedTasks++;
		Future<?> future;
		try {
			future = executorService.submit(handle::_executeCallable);
		} catch (RejectedExecutionException e) {
			handle.getExecutionCoordinator()._log(LogLevel.INTERNAL_ERROR, handle, e.toString());
			return;
		}
		handle._setFuture(future);
	}

	@Override
	public synchronized void close() {
		if (shutdownRequired) {
			executorService.shutdownNow();
		}
	}
}
