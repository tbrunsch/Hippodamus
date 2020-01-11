package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.handles.Handle;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

class ExecutionConfiguration
{
	private final @Nullable String		name;
	private final int					taskType;
	private final Collection<Handle>	dependencies;

	ExecutionConfiguration(String name, int taskType, Collection<Handle> dependencies) {
		this.name = name;
		this.taskType = taskType;
		this.dependencies = dependencies;
	}

	Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	int getTaskType() {
		return taskType;
	}

	Collection<Handle> getDependencies() {
		return dependencies;
	}
}
