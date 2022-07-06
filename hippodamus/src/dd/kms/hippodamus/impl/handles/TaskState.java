package dd.kms.hippodamus.impl.handles;

/**
 * Stores information about whether a task has finished regularly or exceptionally and,
 * if so, what the result was or which exception had been thrown, respectively.
 */
class TaskState<V>
{
	private volatile boolean	finished;
	private volatile V			result;
	private volatile Throwable	exception;
	private volatile TaskStage	taskStage;

	TaskState() {
		taskStage = TaskStage.INITIAL;
	}

	boolean isExecuting() {
		return taskStage == TaskStage.EXECUTING;
	}

	boolean isOnHold() {
		return taskStage == TaskStage.ON_HOLD;
	}

	String transitionTo(TaskStage newStage) {
		if (!taskStage.canTransitionTo(newStage)) {
			return "Trying to transition state from '" + taskStage + "' to '" + newStage + "'";
		}
		taskStage = newStage;
		return null;
	}

	boolean isReadyToJoin() {
		return taskStage.isReadyToJoin();
	}

	boolean hasFinished() {
		return finished;
	}

	boolean hasCompleted() {
		// Check the exception instead of the result because null could also be a valid result
		return finished && exception == null;
	}

	boolean hasTerminatedExceptionally() {
		return exception != null;
	}

	void setResult(V result) {
		this.result = result;
		finished = true;
	}

	V getResult() {
		return result;
	}

	void setException(Throwable exception) {
		this.exception = exception;
		finished = true;
	}

	Throwable getException() {
		return exception;
	}
}
