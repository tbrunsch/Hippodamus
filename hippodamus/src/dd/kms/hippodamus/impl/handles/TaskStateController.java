package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.handles.ResultHandle;
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
		if (state.isReadyToJoin()) {
			// handle has terminated regularly or exceptionally or has been stopped before executing started
			return;
		}

		/*
		 * We have to wait. This is not the intended way to use Hippodamus:
		 *
		 * (1) Completion and exception listener won't be called unless the task has terminated. I.e., if they call
		 *     get(), then the task is ready to join.
		 * (2) If this method is called from another task, then this task should have been specified as dependency of
		 *     the calling task. In that case, the calling task would not execute unless this task has terminated.
		 * (3) We do not see why anymore else should try to retrieve the task's value unless the whole coordinator has
		 *     terminated. If this is really required, then the caller must not activate dependency verification.
		 */
		if (verifyDependencies) {
			synchronized (coordinator) {
				String error = "Waiting for task '" + taskName + "' that has not yet finished. Did you forget to specify its handle as dependency?";
				_log(LogLevel.INTERNAL_ERROR, error);
				throw new CoordinatorException(error);
			}
		}
		/*
		 * We provide limited support for interruptions: We do not swallow interruption requests, but we also do
		 * not react to them here. The reason is that reacting to interrupts would only be possible by also throwing
		 * an InterruptedException. However, this would complicate the usage of ResultHandle.get() just because of
		 * scenarios we discourage.
		 */
		boolean interrupted = Thread.interrupted();
		while (!state.isReadyToJoin()) {
			try {
				joinFlag.waitUntilTrue();
				interrupted = interrupted || Thread.interrupted();
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
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
