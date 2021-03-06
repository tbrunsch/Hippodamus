package dd.kms.hippodamus.handles;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.execution.InternalTaskHandle;
import dd.kms.hippodamus.logging.LogLevel;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

class ResultHandleImpl<T> implements ResultHandle<T>
{
	private static final Consumer<Handle>	NO_HANDLE_CONSUMER	= handle -> {};

	private final InternalCoordinator					coordinator;
	private final String								taskName;
	private final int									id;
	private final ExecutorServiceWrapper				executorServiceWrapper;
	private final StoppableExceptionalCallable<T, ?>	callable;
	private final boolean								verifyDependencies;

	private final List<Runnable>						completionListeners					= new ArrayList<>();
	private final List<Runnable>						exceptionListeners					= new ArrayList<>();

	private final HandleState<T>						state;

	private volatile InternalTaskHandle					taskHandle;

	ResultHandleImpl(InternalCoordinator coordinator, String taskName, int id, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies, boolean stopped) {
		this.coordinator = coordinator;
		this.taskName = taskName;
		this.id = id;
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
		this.verifyDependencies = verifyDependencies;
		this.state = new HandleState<>(this, coordinator, stopped);
	}

	/*****************
	 * Stage Changes *
	 ****************/
	@Override
	public void submit() {
		synchronized (coordinator) {
			if (!state.isStopped() && state.transitionTo(HandleStage.SUBMITTED)) {
				taskHandle = executorServiceWrapper.submit(id, this::executeCallable);
			}
		}
	}

	/**
	 * @return true if task should be executed
	 */
	private boolean startExecution() {
		synchronized (coordinator) {
			return !state.isStopped() && state.transitionTo(HandleStage.EXECUTING);
		}
	}

	private void complete(T result) {
		synchronized (coordinator) {
			try {
				if (state.setResult(result)) {
					notifyListeners(completionListeners, "completion listener", coordinator::onCompletion);
				}
				executorServiceWrapper.onTaskCompleted();
			} finally {
				state.transitionTo(HandleStage.TERMINATED);
			}
		}
	}

	private void terminateExceptionally(Throwable exception) {
		synchronized (coordinator) {
			try {
				if (state.setException(exception)) {
					notifyListeners(exceptionListeners, "exception listener", coordinator::onException);
				}
			} finally {
				state.transitionTo(HandleStage.TERMINATED);
			}
		}
	}

	@Override
	public final void stop() {
		synchronized (coordinator) {
			HandleStage oldHandleStage = state.getHandleStage();
			try {
				if (!state.stop()) {
					return;
				}
				if (oldHandleStage == HandleStage.EXECUTING) {
					// since we stop the task, the current result type won't change anymore
					state.onResultTypeDetermined();
				} else {
					state.transitionTo(HandleStage.TERMINATED);
				}
				coordinator.stopDependentHandles(this);
				if (taskHandle != null) {
					taskHandle.stop();
				}
			} finally {
				if (oldHandleStage == HandleStage.EXECUTING && coordinator.getWaitMode() != WaitMode.UNTIL_TERMINATION) {
					state.releaseCoordinator();
				}
			}
		}
	}

	@Override
	public void join() {
		state.waitUntilResultTypeDetermined(taskName, verifyDependencies);
	}

	@Override
	public boolean hasCompleted() {
		return state.hasCompleted();
	}

	@Override
	public final boolean hasStopped() {
		return state.isStopped();
	}

	@Override
	public @Nullable Throwable getException() {
		return state.getException();
	}

	@Override
	public String getTaskName() {
		return taskName;
	}

	@Override
	public final InternalCoordinator getExecutionCoordinator() {
		return coordinator;
	}

	@Override
	public T get() {
		join();
		return state.getResult(true);
	}

	private void executeCallable() {
		if (!startExecution()) {
			return;
		}
		try {
			T result = callable.call(this::hasStopped);
			complete(result);
		} catch (TaskStoppedException e) {
			state.transitionTo(HandleStage.TERMINATED);
			stop();
		} catch (Throwable throwable) {
			terminateExceptionally(throwable);
		}
	}

	/***********************
	 * Listener Management *
	 **********************/
	@Override
	public void onCompletion(Runnable listener) {
		synchronized (coordinator) {
			completionListeners.add(listener);
			if (state.hasCompleted()) {
				// only run this listener; other listeners have already been notified
				notifyListeners(Collections.singletonList(listener), "completion listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	@Override
	public void onException(Runnable listener) {
		synchronized (coordinator) {
			exceptionListeners.add(listener);
			if (state.hasTerminatedExceptionally()) {
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
