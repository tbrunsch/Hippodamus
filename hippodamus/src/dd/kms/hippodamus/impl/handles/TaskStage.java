package dd.kms.hippodamus.impl.handles;

import java.util.concurrent.ExecutorService;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Describes the stage a task is in. The following diagram shows the possible stage transitions.
 * <pre>
 *                  ------- ON_HOLD ----------------------------------
 *                  ↓          ↑                                      |
 *     INITIAL -→ READY -> SUBMITTED -→ EXECUTING -→ FINISHED         |
 *       ↓          ↓          ↓                        ↓             ↓
 *       -------------------------------------------------------→ TERMINATED
 * </pre>
 */
public enum TaskStage
{
	/**
	 * The task has not yet been submitted.
	 */
	INITIAL("initial state"),

	/**
	 * All tasks the tasks depends on have completed successfully. The task will immediately be submitted to the
	 * underlying {@link ExecutorService} as long as no maximum parallelism has been specified. If this is the case
	 * and there are already enough tasks in the {@code ExecutorService}'s pipeline, then the task will be queued
	 * internally and submitted later.
	 */
	READY("ready"),

	/**
	 * The task has been submitted to the underlying {@link ExecutorService}.
	 */
	SUBMITTED("submitted"),

	/**
	 * The task execution has been put on hold because one of the resources required by the task was not available
	 * just before the task was going to be executed. The {@code Resource} decides when the task is submitted again.
	 */
	ON_HOLD("on hold"),

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

	private static final ListMultimap<TaskStage, TaskStage> SUCCESSOR_STATES;

	static {
		SUCCESSOR_STATES = ArrayListMultimap.create();

		// add default transition chain
		SUCCESSOR_STATES.put(TaskStage.INITIAL, TaskStage.READY);
		SUCCESSOR_STATES.put(TaskStage.READY, TaskStage.SUBMITTED);
		SUCCESSOR_STATES.put(TaskStage.SUBMITTED, TaskStage.EXECUTING);
		SUCCESSOR_STATES.put(TaskStage.EXECUTING, TaskStage.FINISHED);

		// add transition to TERMINATED except from EXECUTING and TERMINATED
		for (TaskStage taskStage : TaskStage.values()) {
			if (taskStage != TaskStage.EXECUTING && taskStage != TaskStage.TERMINATED) {
				SUCCESSOR_STATES.put(taskStage, TaskStage.TERMINATED);
			}
		}

		// add transitions SUBMITTED -> ON_HOLD -> READY
		SUCCESSOR_STATES.put(TaskStage.SUBMITTED, TaskStage.ON_HOLD);
		SUCCESSOR_STATES.put(TaskStage.ON_HOLD, TaskStage.READY);
	}

	private final String	description;

	TaskStage(String description) {
		this.description = description;
	}

	boolean isReadyToJoin() {
		return compareTo(FINISHED) >= 0;
	}

	boolean canTransitionTo(TaskStage nextStage) {
		return SUCCESSOR_STATES.get(this).contains(nextStage);
	}

	@Override
	public String toString() {
		return description;
	}
}
