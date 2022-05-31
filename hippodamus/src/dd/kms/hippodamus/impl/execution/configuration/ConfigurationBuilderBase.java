package dd.kms.hippodamus.impl.execution.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.Collection;

/**
 * Base class for {@link ExecutionConfigurationBuilderImpl} and {@link AggregationConfigurationBuilderImpl}
 * to avoid implementing all methods of {@link dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder}
 * by delegating to the super method and returning a more concrete type.
 */
abstract class ConfigurationBuilderBase<C extends ExecutionCoordinatorImpl, B extends ExecutionConfigurationBuilder> implements ExecutionConfigurationBuilder
{
	final C						coordinator;

	private @Nullable String	name			= null;
	private TaskType			taskType		= TaskType.COMPUTATIONAL;
	private Collection<Handle>	dependencies	= ImmutableList.of();

	ConfigurationBuilderBase(C coordinator) {
		this.coordinator = coordinator;
	}

	abstract B getBuilder();

	@Override
	public B name(String name) {
		this.name = name;
		return getBuilder();
	}

	@Override
	public B taskType(TaskType type) {
		Preconditions.checkArgument(coordinator.supportsTaskType(taskType), "No ExecutorService has been specified for task type " + taskType);
		this.taskType = type;
		return getBuilder();
	}

	@Override
	public B dependencies(Handle... dependencies) {
		return dependencies(ImmutableList.copyOf(dependencies));
	}

	@Override
	public B dependencies(Collection<? extends Handle> dependencies) {
		this.dependencies = ImmutableList.copyOf(dependencies);
		Handle dependencyWithWrongCoordinator = this.dependencies.stream()
			.filter(dependency -> dependency.getExecutionCoordinator() != coordinator)
			.findFirst()
			.orElse(null);
		if (dependencyWithWrongCoordinator != null) {
			String error = MessageFormat.format("Task '{0}' ({1}) is not managed by this coordinator",
				dependencyWithWrongCoordinator.getTaskName(),
				dependencyWithWrongCoordinator
			);
			throw new IllegalArgumentException(error);
		}
		return getBuilder();
	}

	@Override
	public <T extends Throwable> Handle execute(ExceptionalRunnable<T> runnable) {
		ExceptionalCallable<Void, T> callable = () -> {
			runnable.run();
			return null;
		};
		return execute(callable);
	}

	@Override
	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) {
		return coordinator.execute(callable, createConfiguration());
	}

	TaskConfiguration createConfiguration() {
		return new TaskConfiguration(name, taskType, dependencies);
	}
}
