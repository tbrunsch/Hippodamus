package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import java.util.concurrent.ExecutorService;

/**
 * Describes the stage a task is in. The following diagram shows the possible stage transitions.
 * <pre>
 *     INITIAL -> SUBMITTED -> EXECUTING -> FINISHED
 *       |            |            |           |
 *       ----------------------------------------------> TERMINATED
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
	 * The task has finished, either regularly or exceptionally, but we still have to notify listeners.
	 */
	FINISHED("execution finished"),

	/**
	 * The task has terminated and we have notified all listeners (if required). The coordinator's {@code close()}
	 * method will not return unless all tasks have terminated. This guarantees that all listeners
	 * will be informed before the coordinator shuts down.
	 */
	TERMINATED("terminated");

	private final String	description;

	TaskStage(String description) {
		this.description = description;
	}

	public boolean isReadyToJoin() {
		return compareTo(FINISHED) >= 0;
	}

	@Override
	public String toString() {
		return description;
	}
}
