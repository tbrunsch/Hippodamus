package dd.kms.hippodamus.impl.handles;

/**
 * Stores information about whether a task has finished regularly or exceptionally and,
 * if so, what the result was or which exception had been thrown, respectively.
 */
class TaskState<T>
{
	private volatile boolean	finished;
	private volatile T			result;
	private volatile Throwable	exception;
	private volatile TaskStage	taskStage;

	TaskState() {
		taskStage = TaskStage.INITIAL;
	}

	boolean isExecuting() {
		return taskStage == TaskStage.EXECUTING;
	}

	String transitionTo(TaskStage newStage) {
		if (newStage != TaskStage.TERMINATED && newStage.ordinal() != taskStage.ordinal() + 1) {
			return "Trying to transition state from '" + taskStage + "' to '" + newStage + "'";
		}
		taskStage = newStage;
		return null;
	}

	boolean hasTerminated() {
		return taskStage.isTerminalStage();
	}

	boolean hasFinished() {
		return finished;
	}

	boolean hasCompleted() {
		// Check the exception instead of the result because null could also be a valid result
		return finished && exception == null;
	}

	boolean hasTerminatedExceptionally() {
		return finished && exception != null;
	}

	boolean setResult(T result) {
		if (finished) {
			return false;
		}
		this.result = result;
		finished = true;
		return true;
	}

	T getResult() {
		return result;
	}

	boolean setException(Throwable exception) {
		if (finished) {
			return false;
		}
		this.exception = exception;
		finished = true;
		return true;
	}

	Throwable getException() {
		return exception;
	}
}
