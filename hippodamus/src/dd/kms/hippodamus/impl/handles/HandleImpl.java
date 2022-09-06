package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.resources.ResourceRequestor;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.impl.resources.ResourceRequestorImpl;
import dd.kms.hippodamus.impl.resources.ResourceShare;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class HandleImpl<V> implements ResultHandle<V>
{
	private static final Consumer<Handle>	NO_HANDLE_CONSUMER	= handle -> {};

	private final ResourceRequestor			resourceRequestor					= new ResourceRequestorImpl(this);

	private final ExecutionCoordinatorImpl	coordinator;
	private final String					taskName;
	private final int						id;
	private final ExecutorServiceWrapper	executorServiceWrapper;
	private final ExceptionalCallable<V, ?> callable;
	private final ResourceShare				requiredResourceShare;
	private final boolean					verifyDependencies;

	private final List<Runnable>			completionListeners					= new ArrayList<>();
	private final List<Runnable>			exceptionListeners					= new ArrayList<>();

	private final TaskStateController<V>	stateController;

	/**
	 * Only used for stopping the task.
	 */
	private Future<?>						_future;

	/**
	 * Used to request the interrupting of the current task. This is necessary because common implementations
	 * of {@link Future#cancel(boolean)} ignore the Boolean flag. This is particularly the case for {@link java.util.concurrent.ForkJoinTask}
	 * that is returned by {@link java.util.concurrent.ForkJoinPool}, which is used by default for computational
	 * tasks. This is why we interrupt the executing thread ourselves if the task is requested to be stopped and
	 * finally clear the interruption flag of the thread again.
	 */
	private Thread							_executingThread;

	public HandleImpl(ExecutionCoordinatorImpl coordinator, String taskName, int id, ExecutorServiceWrapper executorServiceWrapper, ExceptionalCallable<V, ?> callable, ResourceShare requiredResourceShare, boolean verifyDependencies) {
		this.coordinator = coordinator;
		this.taskName = taskName;
		this.id = id;
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
		this.requiredResourceShare = requiredResourceShare;
		this.verifyDependencies = verifyDependencies;
		this.stateController = new TaskStateController<>(this, coordinator);
	}

	public int getId() {
		return id;
	}

	/*****************
	 * Stage Changes *
	 ****************/
	public void submit() {
		synchronized (coordinator) {
			if (!coordinator._hasStopped() && stateController._transitionTo(TaskStage.SUBMITTED)) {
				try {
					requiredResourceShare.addPendingResourceShare();
				} catch (Throwable t) {
					logUnexpectedException("Exception when trying to update pending resource shares", t);
					stateController._transitionTo(TaskStage.TERMINATED);
				}
				executorServiceWrapper._submit(this);
			}
		}
	}

	private void complete(V result) {
		synchronized (coordinator) {
			stateController._setResult(result);
			_notifyListeners(completionListeners, "completion listener", coordinator::onCompletion);
			executorServiceWrapper._onExecutionCompleted();
			_terminate();
		}
	}

	private void terminateExceptionally(Throwable exception) {
		synchronized (coordinator) {
			stateController._setException(exception);
			_notifyListeners(exceptionListeners, "exception listener", coordinator::onException);
			_terminate();
		}
	}

	private void _terminate() {
		try {
			requiredResourceShare.release();
		} catch (Throwable t) {
			logUnexpectedException("Exception when releasing resource share", t);
		}
		stateController._transitionTo(TaskStage.TERMINATED);
		_executingThread = null;
		_future = null;
	}

	public void _stop() {
		if (coordinator._hasStopped()) {
			return;
		}
		TaskStage taskStage = stateController.getTaskStage();
		if (taskStage == TaskStage.EXECUTING) {
			// since we stop the task, the current result type won't change anymore
			_executingThread.interrupt();
			_executingThread = null;
			stateController._makeReadyToJoin();
		} else {
			try {
				if (taskStage == TaskStage.SUBMITTED) {
					requiredResourceShare.removePendingResourceShare();
				} else if (taskStage == TaskStage.ON_HOLD) {
					requiredResourceShare.remove(resourceRequestor);
				}
			} catch (Throwable t) {
				logUnexpectedException("Exception when trying to update resource state when stopping task", t);
			}
			stateController._transitionTo(TaskStage.TERMINATED);
		}
		if (_future != null) {
			_future.cancel(true);
		}
	}

	@Override
	public boolean hasCompleted() {
		return stateController.hasCompleted();
	}

	@Override
	public @Nullable Throwable getException() {
		return stateController.getException();
	}

	@Override
	public String getTaskName() {
		return taskName;
	}

	@Override
	public final ExecutionCoordinatorImpl getExecutionCoordinator() {
		return coordinator;
	}

	@Override
	public V get() {
		stateController.join(taskName, verifyDependencies);
		if (stateController.hasCompleted()) {
			return stateController.getResult();
		} else if (stateController.hasTerminatedExceptionally()) {
			Throwable exception = stateController.getException();
			throw new CompletionException(exception);
		}
		synchronized (coordinator) {
			if (coordinator._hasStopped()) {
				throw new CancellationException("Trying to access value of task '" + taskName + "' that has been stopped");
			}
			String error = "The task has not been stopped nor did it terminate, but has been considered joinable.";
			coordinator._log(LogLevel.INTERNAL_ERROR, this, error);
			throw new CoordinatorException(error);
		}
	}

	public void _setFuture(Future<?> future) {
		this._future = future;
	}

	public void executeCallable() {
		clearInterruptionFlag();

		synchronized (coordinator) {
			if (!_startExecution()) {
				_future = null;
				_executingThread = null;
				return;
			}
		}

		try {
			V result = callable.call();
			complete(result);
		} catch (Throwable throwable) {
			terminateExceptionally(throwable);
		} finally {
			clearInterruptionFlag();
		}
	}

	private void clearInterruptionFlag() {
		Thread.interrupted();
	}

	private boolean _startExecution() {
		boolean permitTaskExecution;
		try {
			permitTaskExecution = requiredResourceShare.tryAcquire(resourceRequestor);
		} catch (Throwable t) {
			logUnexpectedException("Exception when trying to acquire resource", t);
			stateController._transitionTo(TaskStage.TERMINATED);
			return false;
		} finally {
			if (!removePendingResourceShare()) {
				return false;
			}
		}
		if (!permitTaskExecution) {
			stateController._transitionTo(TaskStage.ON_HOLD);
			executorServiceWrapper._onExecutionCompleted();
			return false;
		}

		_executingThread = Thread.currentThread();
		return !coordinator._hasStopped() && stateController._transitionTo(TaskStage.EXECUTING);
	}

	private boolean removePendingResourceShare() {
		try {
			requiredResourceShare.removePendingResourceShare();
			return true;
		} catch (Throwable t) {
			logUnexpectedException("Exception when trying to update pending resource shares", t);
			stateController._transitionTo(TaskStage.TERMINATED);
			return false;
		}
	}

	private void logUnexpectedException(String error, Throwable t) {
		coordinator._log(t, this, error + ": " + t);
	}

	/***********************
	 * Listener Management *
	 **********************/
	@Override
	public void onCompletion(Runnable listener) {
		synchronized (coordinator) {
			completionListeners.add(listener);
			if (stateController.hasCompleted()) {
				// only run this listener; other listeners have already been notified
				_notifyListeners(Collections.singletonList(listener), "completion listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	@Override
	public void onException(Runnable listener) {
		synchronized (coordinator) {
			exceptionListeners.add(listener);
			if (stateController.hasTerminatedExceptionally()) {
				// only inform this handler; other handlers have already been notified
				_notifyListeners(Collections.singletonList(listener), "exception listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	private void _notifyListeners(List<Runnable> listeners, String listenerDescription, Consumer<Handle> coordinatorListener) {
		Throwable listenerException = null;
		Runnable exceptionalListener = null;
		for (Runnable listener : listeners) {
			try {
				listener.run();
			} catch (Throwable t) {
				if (listenerException == null) {
					listenerException = t;
					exceptionalListener = listener;
				}
			}
		}
		if (listenerException == null) {
			coordinatorListener.accept(this);
		} else {
			String error = MessageFormat.format("{0} in {1} \"{2}\"",
				listenerException.getClass().getSimpleName(),
				listenerDescription,
				exceptionalListener);
			logUnexpectedException(error, listenerException);
		}
	}
}
