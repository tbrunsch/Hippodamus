package dd.kms.hippodamus.impl.handles;

/**
 * Stores information about whether a task has finished regularly or exceptionally and,
 * if so, what the result was or which exception had been thrown, respectively.
 */
class ResultDescription<T>
{
	private volatile boolean	finished;
	private volatile T			result;
	private volatile Throwable	exception;

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
