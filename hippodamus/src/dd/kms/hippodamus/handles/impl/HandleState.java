package dd.kms.hippodamus.handles.impl;

class HandleState
{
	/*
	 * The field is semantically final, but Java prevents declaring it both, final and volatile.
	 * Since it has to be volatile, we cannot declare it final as well.
	 */
	private volatile boolean[]	flags		= new boolean[StateFlag.values().length];

	private volatile Throwable	exception;

	HandleState(boolean completed, boolean stopped) {
		flags[StateFlag.COMPLETED.ordinal()] = completed;
		flags[StateFlag.STOPPED.ordinal()] = stopped;
	}

	HandleState(HandleState that) {
		System.arraycopy(that.flags, 0, flags, 0, flags.length);
		exception = that.exception;
	}

	boolean isFlagSet(StateFlag flag) {
		return flags[flag.ordinal()];
	}

	void setFlag(StateFlag flag) {
		flags[flag.ordinal()] = true;
	}

	Throwable getException() {
		return exception;
	}

	void setException(Throwable exception) {
		this.exception = exception;
	}
}
