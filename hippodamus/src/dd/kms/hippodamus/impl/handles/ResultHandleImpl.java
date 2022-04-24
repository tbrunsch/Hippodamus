package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.handles.TaskStoppedException;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class ResultHandleImpl<T> implements ResultHandle<T>
{
	private static final Consumer<Handle>	NO_HANDLE_CONSUMER	= handle -> {};

	private final ExecutionCoordinatorImpl	coordinator;
	private final String					taskName;
	private final int						id;
	private final ExecutorServiceWrapper	executorServiceWrapper;
	private final ExceptionalCallable<T, ?> callable;
	private final boolean					verifyDependencies;

	private final List<Runnable>			completionListeners					= new ArrayList<>();
	private final List<Runnable>			exceptionListeners					= new ArrayList<>();

	private final TaskStateController<T>	stateController;

	/**
	 * Only used for stopping the task. Since it is only accessed when the coordinator is locked,
	 * it is not necessary to make it {@code volatile}.
	 */
	private Future<?>						future;

	/**
	 * Used to request the interrupting of the current task. This is necessary because common implementations
	 * of {@link Future#cancel(boolean)} ignore the Boolean flag. This is particularly the case for {@link java.util.concurrent.ForkJoinTask}
	 * that is returned by {@link java.util.concurrent.ForkJoinPool}, which is used by default for computational
	 * tasks. This is why we interrupt the executing thread ourselves if the task is requested to be stopped and
	 * finally clear the interruption flag of the thread again.<br>
	 * <br>
	 * Since the field is only accessed when the coordinator is locked, it is not necessary to make it {@code volatile}.
	 */
	private Thread							executingThread;

	public ResultHandleImpl(ExecutionCoordinatorImpl coordinator, String taskName, int id, ExecutorServiceWrapper executorServiceWrapper, ExceptionalCallable<T, ?> callable, boolean verifyDependencies, boolean stopped) {
		this.coordinator = coordinator;
		this.taskName = taskName;
		this.id = id;
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
		this.verifyDependencies = verifyDependencies;
		this.stateController = new TaskStateController<>(this, coordinator, stopped);
	}

	public int getId() {
		return id;
	}

	/*****************
	 * Stage Changes *
	 ****************/
	public void submit() {
		synchronized (coordinator) {
			if (!stateController.hasStopped() && stateController.transitionTo(TaskStage.SUBMITTED)) {
				executorServiceWrapper.submit(this);
			}
		}
	}

	/**
	 * @return true if task should be executed
	 */
	private boolean startExecution() {
		synchronized (coordinator) {
			executingThread = Thread.currentThread();
			return !stateController.hasStopped() && stateController.transitionTo(TaskStage.EXECUTING);
		}
	}

	private void complete(T result) {
		synchronized (coordinator) {
			try {
				if (stateController.setResult(result)) {
					notifyListeners(completionListeners, "completion listener", coordinator::onCompletion);
				}
				executorServiceWrapper.onTaskCompleted();
			} finally {
				stateController.transitionTo(TaskStage.TERMINATED);
				executingThread = null;
			}
		}
	}

	private void terminateExceptionally(Throwable exception) {
		synchronized (coordinator) {
			try {
				if (stateController.setException(exception)) {
					notifyListeners(exceptionListeners, "exception listener", coordinator::onException);
				}
			} finally {
				stateController.transitionTo(TaskStage.TERMINATED);
				executingThread = null;
			}
		}
	}

	@Override
	public final void stop() {
		synchronized (coordinator) {
			boolean isExecuting = stateController.isExecuting();
			try {
				if (!stateController.stop()) {
					return;
				}
				if (isExecuting) {
					// since we stop the task, the current result type won't change anymore
					executingThread.interrupt();
					executingThread = null;
					stateController.onTerminated();
				} else {
					stateController.transitionTo(TaskStage.TERMINATED);
				}
				coordinator.stopDependentHandles(this);
				if (future != null) {
					future.cancel(true);
				}
			} finally {
				if (isExecuting && coordinator.getWaitMode() != WaitMode.UNTIL_TERMINATION) {
					stateController.releaseCoordinator();
				}
			}
		}
	}

	@Override
	public void join() {
		stateController.waitUntilTerminated(taskName, verifyDependencies);
	}

	@Override
	public boolean hasCompleted() {
		return stateController.hasCompleted();
	}

	@Override
	public final boolean hasStopped() {
		return stateController.hasStopped();
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
	public T get() {
		join();
		return stateController.getResult();
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	public void setFuture(Future<?> future) {
		this.future = future;
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	public void executeCallable() {
		clearInterruptionFlag();
		try {
			if (!startExecution()) {
				return;
			}
			T result = callable.call();
			complete(result);
		} catch (TaskStoppedException e) {

			// TODO: Do we have to manually transition the state here or can we rely on stop()?
			stateController.transitionTo(TaskStage.TERMINATED);

			stop();
		} catch (Throwable throwable) {
			terminateExceptionally(throwable);
		} finally {
			clearInterruptionFlag();
		}
	}

	private void clearInterruptionFlag() {
		Thread.interrupted();
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
				notifyListeners(Collections.singletonList(listener), "completion listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	@Override
	public void onException(Runnable listener) {
		synchronized (coordinator) {
			exceptionListeners.add(listener);
			if (stateController.hasTerminatedExceptionally()) {
				// only inform this handler; other handlers have already been notified
				notifyListeners(Collections.singletonList(listener), "exception listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	/**
	 * Ensure that this method is only called when the coordinator is locked.
	 */
	private void notifyListeners(List<Runnable> listeners, String listenerDescription, Consumer<Handle> coordinatorListener) {
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
			String message = MessageFormat.format("{0} in {1} \"{2}\": {3}",
				listenerException.getClass().getSimpleName(),
				listenerDescription,
				exceptionalListener,
				listenerException.getMessage());
			coordinator.log(LogLevel.INTERNAL_ERROR, this, message);
		}
	}
}
