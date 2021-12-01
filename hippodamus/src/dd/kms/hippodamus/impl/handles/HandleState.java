package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.TaskStoppedException;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.impl.coordinator.InternalCoordinator;

import java.util.concurrent.Semaphore;

class HandleState<T>
{
	private final Handle handle;
	private final InternalCoordinator	coordinator;

	private final ResultDescription<T>	resultDescription	= new ResultDescription<>();
	private volatile HandleStage		handleStage			= HandleStage.INITIAL;
	private volatile boolean			stopped				= false;

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * is stopped. It is meant to be waited for in the {@link Handle#join()}-method.<br>
	 * <br>
	 * Note that the value must be set to true <b>before</b> calling any listener to avoid deadlocks:
	 * Listeners, in particular completion listeners, might indirectly call {@code join()}, e.g., by calling
	 * {@link ResultHandleImpl#get()}.
	 */
	private final AwaitableBoolean resultTypeDeterminedFlag;

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * is stopped. It operates on the coordinator's termination lock we obtain by calling
	 * {@link InternalCoordinator#getTerminationLock()}.<br>
	 * Note that the value must be set to true <b>after</b> calling any listener to ensure that the
	 * coordinator does not close before notifying all listeners.
	 */
	private final AwaitableBoolean		releaseCoordinatorFlag;

	HandleState(Handle handle, InternalCoordinator coordinator, boolean stopped) {
		this.handle = handle;
		this.coordinator = coordinator;
		this.stopped = stopped;
		checkState();

		resultTypeDeterminedFlag = new AwaitableBoolean(new Semaphore(1));
		releaseCoordinatorFlag = new AwaitableBoolean(coordinator.getTerminationLock());

		if (!stopped) {
			try {
				resultTypeDeterminedFlag.setFalse();
				releaseCoordinatorFlag.setFalse();
			} catch (InterruptedException e) {
				handle.stop();
			}
		}
	}

	boolean setResult(T result) {
		return !stopped
			&& checkCondition(resultDescription.setResult(result), "Cannot set result due to inconsistent state")
			&& log(LogLevel.STATE, "result = " + result)
			&& transitionTo(HandleStage.TERMINATING);
	}

	boolean setException(Throwable exception) {
		return !stopped
			&& checkCondition(resultDescription.setException(exception), "Cannot set exception due to inconsistent state")
			&& log(LogLevel.STATE, "encountered " + exception.getClass().getSimpleName() + ": " + exception.getMessage())
			&& transitionTo(HandleStage.TERMINATING);
	}

	boolean stop() {
		if (stopped) {
			return false;
		}
		stopped = true;
		log(LogLevel.STATE, "stopped");
		checkState();
		return true;
	}

	boolean isStopped() {
		return stopped;
	}

	boolean hasCompleted() {
		return resultDescription.getResultType() == ResultType.COMPLETED;
	}

	T getResult(boolean checkExistence) {
		if (checkExistence) {
			checkCondition(resultDescription.getResultType() == ResultType.COMPLETED, "Trying to access unavailable result");
		}
		return resultDescription.getResult();
	}

	Throwable getException() {
		return resultDescription.getException();
	}

	boolean hasTerminatedExceptionally() {
		return resultDescription.getResultType() == ResultType.EXCEPTION;
	}

	HandleStage getHandleStage() {
		return handleStage;
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
	boolean transitionTo(HandleStage newStage) {
		if (handleStage.compareTo(HandleStage.TERMINATING) < 0 && HandleStage.TERMINATING.compareTo(newStage) <= 0) {
			onResultTypeDetermined();
		}
		if (newStage == HandleStage.TERMINATED) {
			if (handleStage != HandleStage.TERMINATED) {
				releaseCoordinator();
			}
		} else {
			if (!checkCondition(newStage.ordinal() == handleStage.ordinal() + 1, "Trying to transition state from '" + handleStage + "' to '" + newStage + "'")) {
				return false;
			}
		}
		handleStage = newStage;
		log(LogLevel.STATE, handleStage.toString());
		return checkState();
	}

	void waitUntilResultTypeDetermined(String taskName, boolean verifyDependencies) {
		if (hasCompleted()) {
			return;
		}
		if (verifyDependencies) {
			synchronized (coordinator) {
				log(LogLevel.INTERNAL_ERROR, "Waiting for a handle that has not yet completed. Did you forget to specify that handle as dependency?");
				throw new TaskStoppedException(taskName);
			}
		}
		if (stopped || resultDescription.getResultType() == ResultType.EXCEPTION) {
			throw new TaskStoppedException(taskName);
		}
		resultTypeDeterminedFlag.waitUntilTrue();
		if (resultDescription.getResultType() != ResultType.COMPLETED) {
			throw new TaskStoppedException(taskName);
		}
	}

	/***********
	 * Locking *
	 **********/
	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	void onResultTypeDetermined() {
		resultTypeDeterminedFlag.setTrue();
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	void releaseCoordinator() {
		releaseCoordinatorFlag.setTrue();
	}

	/***************************************************************
	 * Consistency Checks                                          *
	 *                                                             *
	 * Ensure that they are called before any concurrency problems *
	 * may occur or with locking the coordinator.                  *
	 **************************************************************/
	private boolean checkState() {
		boolean success = true;
		ResultType resultType = resultDescription.getResultType();
		if (handleStage == HandleStage.INITIAL || handleStage == HandleStage.SUBMITTED || handleStage == HandleStage.EXECUTING) {
			success = success && checkCondition(resultType == ResultType.NONE, "The result should still be undefined");
		}
		if (resultType != ResultType.NONE) {
			success = success && checkCondition(handleStage == HandleStage.TERMINATING || handleStage == HandleStage.TERMINATED, "The task should have terminated");
		}
		return success;
	}

	private boolean checkCondition(boolean condition, String message) {
		if (!condition) {
			coordinator.log(LogLevel.INTERNAL_ERROR, handle, message);
		}
		return condition;
	}
}
