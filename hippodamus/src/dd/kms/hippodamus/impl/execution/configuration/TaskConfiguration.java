package dd.kms.hippodamus.impl.execution.configuration;

import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.handles.Handle;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

/**
 * Stores all information that can be configured by a {@link dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder}
 * (or a {@link dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder}).
 */
public class TaskConfiguration
{
	private final @Nullable String		name;
	private final TaskType				taskType;
	private final Collection<Handle>	dependencies;

	TaskConfiguration(String name, TaskType taskType, Collection<Handle> dependencies) {
		this.name = name;
		this.taskType = taskType;
		this.dependencies = dependencies;
	}

	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	public TaskType getTaskType() {
		return taskType;
	}

	public Collection<Handle> getDependencies() {
		return dependencies;
	}
}
