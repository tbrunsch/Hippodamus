package dd.kms.hippodamus.handles;

class ResultDescription<T>
{
	private volatile ResultType	resultType	= ResultType.NONE;
	private volatile T			result;
	private volatile Throwable	exception;

	ResultType getResultType() {
		return resultType;
	}

	boolean setResult(T result) {
		if (resultType != ResultType.NONE) {
			return false;
		}
		this.result = result;
		resultType = ResultType.COMPLETED;
		return true;
	}

	T getResult() {
		return result;
	}

	boolean setException(Throwable exception) {
		if (resultType != ResultType.NONE) {
			return false;
		}
		this.exception = exception;
		resultType = ResultType.EXCEPTION;
		return true;
	}

	Throwable getException() {
		return exception;
	}
}
