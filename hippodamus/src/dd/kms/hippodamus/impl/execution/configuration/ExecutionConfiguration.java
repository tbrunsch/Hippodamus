package dd.kms.hippodamus.impl.execution.configuration;

import dd.kms.hippodamus.api.handles.Handle;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

public class ExecutionConfiguration
{
	private final @Nullable String		name;
	private final int					taskType;
	private final Collection<Handle>	dependencies;

	ExecutionConfiguration(String name, int taskType, Collection<Handle> dependencies) {
		this.name = name;
		this.taskType = taskType;
		this.dependencies = dependencies;
	}

	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	public int getTaskType() {
		return taskType;
	}

	public Collection<Handle> getDependencies() {
		return dependencies;
	}
}
