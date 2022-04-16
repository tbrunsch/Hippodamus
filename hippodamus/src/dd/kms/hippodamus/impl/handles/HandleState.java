package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.TaskStoppedException;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;

class HandleState<T>
{
	private final Handle 					handle;
	private final ExecutionCoordinatorImpl	coordinator;

	private final ResultDescription<T>		resultDescription	= new ResultDescription<>();
	private volatile TaskStage				taskStage			= TaskStage.INITIAL;

	/**
	 * Describes whether the task has been stopped or not. The flag is set while the coordinator
	 * is locked.
	 */
	private volatile boolean				stopped;

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

	HandleState(Handle handle, ExecutionCoordinatorImpl coordinator, boolean stopped) {
		this.handle = handle;
		this.coordinator = coordinator;
		this.stopped = stopped;
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
		return !stopped
			&& checkCondition(resultDescription.setResult(result), "Cannot set result due to inconsistent state")
			&& log(LogLevel.STATE, "result = " + result)
			&& transitionTo(TaskStage.TERMINATING);
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	boolean setException(Throwable exception) {
		return !stopped
			&& checkCondition(resultDescription.setException(exception), "Cannot set exception due to inconsistent state")
			&& log(LogLevel.STATE, "encountered " + exception.getClass().getSimpleName() + ": " + exception.getMessage())
			&& transitionTo(TaskStage.TERMINATING);
	}

	/**
	 * Ensure that this method is called with locking the coordinator.
	 */
	boolean stop() {
		if (stopped) {
			return false;
		}
		stopped = true;
		log(LogLevel.STATE, "stopped");
		checkState();
		return true;
	}

	boolean hasStopped() {
		return stopped;
	}

	boolean hasCompleted() {
		return resultDescription.hasCompleted();
	}

	T getResult() {
		checkCondition(resultDescription.hasCompleted(), "Trying to access unavailable result");
		return resultDescription.getResult();
	}

	Throwable getException() {
		return resultDescription.getException();
	}

	boolean hasTerminatedExceptionally() {
		return resultDescription.hasTerminatedExceptionally();
	}

	boolean isExecuting() {
		return taskStage == TaskStage.EXECUTING;
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
		if (taskStage.compareTo(TaskStage.TERMINATING) < 0 && TaskStage.TERMINATING.compareTo(newStage) <= 0) {
			onTerminated();
		}
		if (newStage == TaskStage.TERMINATED) {
			if (taskStage != TaskStage.TERMINATED) {
				releaseCoordinator();
			}
		} else {
			if (!checkCondition(newStage.ordinal() == taskStage.ordinal() + 1, "Trying to transition state from '" + taskStage + "' to '" + newStage + "'")) {
				return false;
			}
		}
		taskStage = newStage;
		log(LogLevel.STATE, taskStage.toString());
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
		if (stopped || resultDescription.hasTerminatedExceptionally()) {
			throw new TaskStoppedException(taskName);
		}
		boolean completed;
		try {
			terminatedFlag.waitUntilTrue();
			completed = resultDescription.hasCompleted();
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
			!resultDescription.hasFinished() || taskStage == TaskStage.TERMINATING || taskStage == TaskStage.TERMINATED,
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
