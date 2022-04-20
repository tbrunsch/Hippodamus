package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.TaskStoppedException;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;

class TaskStateController<T>
{
	private final Handle 					handle;
	private final ExecutionCoordinatorImpl	coordinator;

	private final TaskState<T>				state;

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * is stopped. It is meant to be waited for in the {@link Handle#join()}-method.<br>
	 * <br>
	 * Note that the value must be set to true <b>before</b> calling any listener to avoid deadlocks:
	 * Listeners, in particular completion listeners, might indirectly call {@code join()}, e.g., by calling
	 * {@link ResultHandleImpl#get()}.
	 */
	private final AwaitableFlag				terminatedFlag;

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * is stopped. It operates on the coordinator's termination lock we obtain by calling
	 * {@link ExecutionCoordinatorImpl#getTerminationLock()}.<br>
	 * Note that the value must be set to true <b>after</b> calling any listener to ensure that the
	 * coordinator does not close before notifying all listeners.
	 */
	private final AwaitableFlag				releaseCoordinatorFlag;

	TaskStateController(Handle handle, ExecutionCoordinatorImpl coordinator, boolean stopped) {
		this.handle = handle;
		this.coordinator = coordinator;
		this.state = new TaskState<>(stopped);
		checkState();

		terminatedFlag = new AwaitableFlag();
		releaseCoordinatorFlag = new AwaitableFlag(coordinator.getTerminationLock());

		if (!stopped) {
			try {
				terminatedFlag.unset();
				releaseCoordinatorFlag.unset();
			} catch (InterruptedException e) {
				handle.stop();
			}
		}
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	boolean setResult(T result) {
		return !state.hasStopped()
			&& checkCondition(state.setResult(result), "Cannot set result due to inconsistent state")
			&& log(LogLevel.STATE, "result = " + result)
			&& transitionTo(TaskStage.TERMINATING);
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	boolean setException(Throwable exception) {
		return !state.hasStopped()
			&& checkCondition(state.setException(exception), "Cannot set exception due to inconsistent state")
			&& log(LogLevel.STATE, "encountered " + exception.getClass().getSimpleName() + ": " + exception.getMessage())
			&& transitionTo(TaskStage.TERMINATING);
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	boolean stop() {
		if (!state.stop()) {
			return false;
		}
		log(LogLevel.STATE, "stopped");
		checkState();
		return true;
	}

	boolean hasStopped() {
		return state.hasStopped();
	}

	boolean hasCompleted() {
		return state.hasCompleted();
	}

	T getResult() {
		checkCondition(state.hasCompleted(), "Trying to access unavailable result");
		return state.getResult();
	}

	Throwable getException() {
		return state.getException();
	}

	boolean hasTerminatedExceptionally() {
		return state.hasTerminatedExceptionally();
	}

	boolean isExecuting() {
		return state.isExecuting();
	}

	/**
	 * Ensure that this method is only called when the coordinator is locked.<br>
	 * <br>
	 * The return value is always {@code true} such that this method can be used
	 * within Boolean expressions.
	 */
	private boolean log(LogLevel logLevel, String message) {
		coordinator.log(logLevel, handle, message);
		return true;
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	boolean transitionTo(TaskStage newStage) {
		if (!state.hasTerminated() && newStage.isTerminalStage()) {
			onTerminated();
		}
		String transitionError = state.transitionTo(newStage);
		if (!checkCondition(transitionError == null, transitionError)) {
			return false;
		}
		if (newStage == TaskStage.TERMINATED) {
			releaseCoordinator();
		}
		log(LogLevel.STATE, newStage.toString());
		return checkState();
	}

	void waitUntilTerminated(String taskName, boolean verifyDependencies) {
		if (hasCompleted()) {
			return;
		}
		if (verifyDependencies) {
			synchronized (coordinator) {
				log(LogLevel.INTERNAL_ERROR, "Waiting for a handle that has not yet completed. Did you forget to specify that handle as dependency?");
				throw new TaskStoppedException(taskName);
			}
		}
		if (state.hasStopped() || state.hasTerminatedExceptionally()) {
			throw new TaskStoppedException(taskName);
		}
		boolean completed;
		try {
			terminatedFlag.waitUntilTrue();
			completed = state.hasCompleted();
		} catch (InterruptedException e) {
			completed = false;
		}
		if (!completed) {
			throw new TaskStoppedException(taskName);
		}
	}

	/***********
	 * Locking *
	 **********/
	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	void onTerminated() {
		terminatedFlag.set();
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	void releaseCoordinator() {
		releaseCoordinatorFlag.set();
	}

	/***************************************************************
	 * Consistency Checks                                          *
	 *                                                             *
	 * Ensure that they are called before any concurrency problems *
	 * may occur or with locking the coordinator.                  *
	 **************************************************************/
	private boolean checkState() {
		return checkCondition(
			!state.hasFinished() || state.hasTerminated(),
			"The task should have terminated"
		);
	}

	private boolean checkCondition(boolean condition, String message) {
		if (!condition) {
			synchronized (coordinator) {
				coordinator.log(LogLevel.INTERNAL_ERROR, handle, message);
			}
		}
		return condition;
	}
}
