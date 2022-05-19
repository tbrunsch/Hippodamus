package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
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
 *     <li>The stage changes from {@link TaskStage#EXECUTING} to {@link TaskStage#FINISHED}</li>
 *     <li>The lock that makes callers of {@link ResultHandle#get()} wait is released (see {@link #_transitionTo(TaskStage)})</li>
 *     <li>Completion/exception listeners are informed and</li>
 *     <li>
 *         the stage changes from {@code TaskStage.EXECUTION_FINISHED} to {@link TaskStage#TERMINATED}
 *         (see {@code HandleImpl.complete(Object)} and {@code HandleImpl.terminateExceptionally(Throwable)}, respectively)
 *     </li>
 *     <li>
 *         The lock that forces the {@link ExecutionCoordinator} to wait is released (see {@link #_transitionTo(TaskStage)})
 *     </li>
 * </ol>
 *
 */
class TaskStateController<V>
{
	private final HandleImpl<?>				handle;
	private final ExecutionCoordinatorImpl	coordinator;

	private final TaskState<V>				state;

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * when it is stopped before execution has started. It is meant to be waited for in {@link #join(String, boolean)}.<br>
	 * Note that the value must be set to true <b>before</b> calling any listener to avoid deadlocks:
	 * Listeners, in particular completion listeners, might indirectly call {@code join()}, e.g., by calling
	 * {@link HandleImpl#get()}.
	 */
	private final AwaitableFlag				joinFlag;

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * is stopped. It operates on the coordinator's termination lock we obtain by calling
	 * {@link ExecutionCoordinatorImpl#getTerminationLock()}.<br>
	 * Note that the value must be set to true <b>after</b> calling any listener to ensure that the
	 * coordinator does not close before notifying all listeners.
	 */
	private final AwaitableFlag				releaseCoordinatorFlag;

	TaskStateController(HandleImpl<?> handle, ExecutionCoordinatorImpl coordinator) {
		this.handle = handle;
		this.coordinator = coordinator;
		this.state = new TaskState<>();
		_checkState();

		joinFlag = new AwaitableFlag();
		releaseCoordinatorFlag = new AwaitableFlag(coordinator.getTerminationLock());

		if (!coordinator._hasStopped()) {
			try {
				joinFlag.unset();
				releaseCoordinatorFlag.unset();
			} catch (InterruptedException e) {
				coordinator.stop();
			}
		}
	}

	void _setResult(V result) {
		state.setResult(result);
		_log(LogLevel.STATE, "result = " + result);
		_transitionTo(TaskStage.FINISHED);
	}

	void _setException(Throwable exception) {
		state.setException(exception);
		_log(LogLevel.STATE, "encountered " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
		_transitionTo(TaskStage.FINISHED);
	}

	boolean hasCompleted() {
		return state.hasCompleted();
	}

	V getResult() {
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

	private void _log(LogLevel logLevel, String message) {
		coordinator._log(logLevel, handle, message);
	}

	boolean _transitionTo(TaskStage newStage) {
		boolean wasReadyToJoin = state.isReadyToJoin();
		String transitionError = state.transitionTo(newStage);
		if (!checkCondition(transitionError == null, transitionError)) {
			return false;
		}
		if (!wasReadyToJoin && newStage.isReadyToJoin()) {
			_onTerminated();
		}
		if (newStage == TaskStage.TERMINATED) {
			_releaseCoordinator();
		}
		_log(LogLevel.STATE, newStage.toString());
		return _checkState();
	}

	void join(String taskName, boolean verifyDependencies) {
		if (hasCompleted()) {
			return;
		}
		if (verifyDependencies) {
			synchronized (coordinator) {
				_log(LogLevel.INTERNAL_ERROR, "Waiting for a handle that has not yet completed. Did you forget to specify that handle as dependency?");
				throw new TaskStoppedException(taskName);
			}
		}
		if (state.hasTerminatedExceptionally()) {
			throw new TaskStoppedException(taskName);
		}
		boolean completed;
		try {
			joinFlag.waitUntilTrue();
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
		joinFlag.set();
	}

	void _releaseCoordinator() {
		releaseCoordinatorFlag.set();
	}

	private boolean _checkState() {
		return checkCondition(
			!state.hasFinished() || state.isReadyToJoin(),
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
