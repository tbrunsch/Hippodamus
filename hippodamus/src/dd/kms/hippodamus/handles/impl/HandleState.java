package dd.kms.hippodamus.handles.impl;

class HandleState
{
	private volatile int		flags;

	private volatile Throwable	exception;

	HandleState(boolean completed, boolean stopped) {
		if (completed) {
			setFlag(StateFlag.COMPLETED);
		}
		if (stopped) {
			setFlag(StateFlag.STOPPED);
		}
	}

	boolean isFlagSet(StateFlag flag) {
		return (flags & flag.getBitMask()) != 0;
	}

	void setFlag(StateFlag flag) {
		flags |= flag.getBitMask();
	}

	Throwable getException() {
		return exception;
	}

	void setException(Throwable exception) {
		this.exception = exception;
	}
}
