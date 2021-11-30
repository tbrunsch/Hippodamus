package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import java.util.concurrent.ExecutorService;

enum HandleStage
{
	/**
	 * The task is not yet submitted.
	 */
	INITIAL("initial state"),

	/**
	 * The task is submitted to the {@link ExecutorServiceWrapper}. It is either
	 * queued by the wrapper or directly submitted to the underlying {@link ExecutorService}.
	 */
	SUBMITTED("submitted"),

	/**
	 * The tasks code is being processed by a thread.
	 */
	EXECUTING("started execution"),

	/**
	 * The task has terminated, but we still have to notify listeners.
	 */
	TERMINATING("terminating..."),

	/**
	 * The task has terminated and we have notified all listeners (if required).
	 */
	TERMINATED("terminated");

	private final String	description;

	HandleStage(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return description;
	}
}
