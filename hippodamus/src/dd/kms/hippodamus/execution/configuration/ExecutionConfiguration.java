package dd.kms.hippodamus.execution.configuration;

import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.resources.ResourceShare;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ExecutionConfiguration
{
	private final @Nullable String			name;
	private final int						taskType;
	private final Collection<Handle>		dependencies;
	private final List<ResourceShare<?>>	requiredResourceShares;

	ExecutionConfiguration(String name, int taskType, Collection<Handle> dependencies, List<ResourceShare<?>> requiredResourceShares) {
		this.name = name;
		this.taskType = taskType;
		this.dependencies = dependencies;
		this.requiredResourceShares = ImmutableList.copyOf(requiredResourceShares);
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

	public List<ResourceShare<?>> getRequiredResourceShares() {
		return requiredResourceShares;
	}
}
