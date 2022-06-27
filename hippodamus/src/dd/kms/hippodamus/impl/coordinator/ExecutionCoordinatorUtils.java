package dd.kms.hippodamus.impl.coordinator;

import dd.kms.hippodamus.impl.execution.configuration.TaskConfiguration;

import java.util.Optional;
import java.util.Set;

class ExecutionCoordinatorUtils
{
	/**
	 * Generates a task name based on the {@link TaskConfiguration} and the task index, taking the already generated
	 * {@code taskNames} into account to prevent multiple tasks from having the same name.<br>
	 * <br>
	 * If a task name is specified by {@code taskConfiguration}, then this name is used as initial suggestion. Otherwise,
	 * a generic name based on the task's index is used. If this initial suggestion has not yet been assigned to any
	 * of the tasks managed by the coordinator, then this suggestion is returned as task name. Otherwise, a name that
	 * has not yet been assigned to any of the tasks is generated by appending suffixes " (2)", " (3)", ...<br>
	 * <br>
	 * The returned task name is also added to the set of task names to ensure that subsequent calls of this method do
	 * not return the same task name again.s
	 */
	static String generateTaskName(TaskConfiguration taskConfiguration, int taskIndex, Set<String> taskNames) {
		Optional<String> taskName = taskConfiguration.getName();
		String nameSuggestion = taskName.isPresent() ? taskName.get() : createGenericTaskName(taskIndex);
		return createUniqueTaskName(nameSuggestion, taskNames);
	}

	private static String createGenericTaskName(int taskIndex) {
		return "Task " + (taskIndex + 1);
	}

	private static String createUniqueTaskName(String suggestion, Set<String> taskNames) {
		String name = suggestion;
		int index = 2;
		while (taskNames.contains(name)) {
			name = suggestion + " (" + index++ + ")";
		}
		taskNames.add(name);
		return name;
	}
}
