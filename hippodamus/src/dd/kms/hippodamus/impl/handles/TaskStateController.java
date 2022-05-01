package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.handles.TaskStoppedException;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;

/**
 * Controls state changes of a task.<br>
 * <br>
 * <b>When a task Finishes</b> (either exceptionally or regularly), the following things happen in the given order:
 * <ol>
 *     <li>The result/exception is stored (see {@link #_setResult(Object)} and {@link #_setException(Throwable)}, respectively)</li>
 *     <li>The stage changes from {@link TaskStage#EXECUTING} to {@link TaskStage#EXECUTION_FINISHED}</li>
 *     <li>The lock that makes callers of {@link ResultHandle#get()} wait is released (see {@link #_transitionTo(TaskStage)})</li>
 *     <li>Completion/exception listeners are informed and</li>
 *     <li>
 *         the stage changes from {@code TaskStage.EXECUTION_FINISHED} to {@link TaskStage#TERMINATED}
 *         (see {@code ResultHandleImpl.complete(Object)} and {@code ResultHandleImpl.terminateExceptionally(Throwable)}, respectively)
 *     </li>
 *     <li>
 *         The lock that forces the {@link ExecutionCoordinator} to wait is released (see {@link #_transitionTo(TaskStage)})
 *     </li>
 * </ol>
 *
 */
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
		_checkState();

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

	boolean _setResult(T result) {
		return !state.hasStopped()
			&& checkCondition(state.setResult(result), "Cannot set result due to inconsistent state")
			&& _log(LogLevel.STATE, "result = " + result)
			&& _transitionTo(TaskStage.EXECUTION_FINISHED);
	}

	boolean _setException(Throwable exception) {
		return !state.hasStopped()
			&& checkCondition(state.setException(exception), "Cannot set exception due to inconsistent state")
			&& _log(LogLevel.STATE, "encountered " + exception.getClass().getSimpleName() + ": " + exception.getMessage())
			&& _transitionTo(TaskStage.EXECUTION_FINISHED);
	}

	boolean _stop() {
		if (!state.stop()) {
			return false;
		}
		_log(LogLevel.STATE, "stopped");
		_checkState();
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
	 * The return value is always {@code true} such that this method can be used
	 * within Boolean expressions.
	 */
	private boolean _log(LogLevel logLevel, String message) {
		coordinator._log(logLevel, handle, message);
		return true;
	}

	boolean _transitionTo(TaskStage newStage) {
		boolean wasInTerminalStage = state.hasTerminated();
		String transitionError = state.transitionTo(newStage);
		if (!checkCondition(transitionError == null, transitionError)) {
			return false;
		}
		if (!wasInTerminalStage && newStage.isTerminalStage()) {
			_onTerminated();
		}
		if (newStage == TaskStage.TERMINATED) {
			_releaseCoordinator();
		}
		_log(LogLevel.STATE, newStage.toString());
		return _checkState();
	}

	void waitUntilTerminated(String taskName, boolean verifyDependencies) {
		if (hasCompleted()) {
			return;
		}
		if (verifyDependencies) {
			synchronized (coordinator) {
				_log(LogLevel.INTERNAL_ERROR, "Waiting for a handle that has not yet completed. Did you forget to specify that handle as dependency?");
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
	void _onTerminated() {
		terminatedFlag.set();
	}

	void _releaseCoordinator() {
		releaseCoordinatorFlag.set();
	}

	private boolean _checkState() {
		return checkCondition(
			!state.hasFinished() || state.hasTerminated(),
			"The task should have terminated"
		);
	}

	private boolean checkCondition(boolean condition, String message) {
		if (!condition) {
			synchronized (coordinator) {
				coordinator._log(LogLevel.INTERNAL_ERROR, handle, message);
			}
		}
		return condition;
	}
}
