package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import java.util.concurrent.ExecutorService;

/**
 * Describes the stage a task is in. The following diagram shows the possible stage transitions.
 * <pre>
 *     INITIAL -> SUBMITTED -> EXECUTING -> TERMINATING
 *       |            |            |             |
 *       ------------------------------------------------> TERMINATED
 * </pre>
 */
enum TaskStage
{
	/**
	 * The task has not yet been submitted.
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

	TaskStage(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return description;
	}
}
