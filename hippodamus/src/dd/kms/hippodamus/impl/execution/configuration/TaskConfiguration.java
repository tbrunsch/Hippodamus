package dd.kms.hippodamus.impl.execution.configuration;

import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.impl.resources.ResourceShare;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Stores all information that can be configured by a {@link dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder}
 * (or a {@link dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder}).
 */
public class TaskConfiguration
{
	private final @Nullable String		name;
	private final TaskType				taskType;
	private final boolean				ignoreResult;
	private final Collection<Handle>	dependencies;
	private final ResourceShare			requiredResourceShare;
	private final Consumer<Handle>		handleConsumer;

	TaskConfiguration(@Nullable String name, TaskType taskType, boolean ignoreResult, Collection<Handle> dependencies, ResourceShare requiredResourceShare, Consumer<Handle> handleConsumer) {
		this.name = name;
		this.taskType = taskType;
		this.ignoreResult = ignoreResult;
		this.dependencies = dependencies;
		this.requiredResourceShare = requiredResourceShare;
		this.handleConsumer = handleConsumer;
	}

	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	public TaskType getTaskType() {
		return taskType;
	}

	public boolean isIgnoreResult() {
		return ignoreResult;
	}

	public Collection<Handle> getDependencies() {
		return dependencies;
	}

	public ResourceShare getRequiredResourceShare() {
		return requiredResourceShare;
	}

	public Consumer<Handle> getHandleConsumer() {
		return handleConsumer;
	}
}
