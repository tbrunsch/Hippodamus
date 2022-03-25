package dd.kms.hippodamus.impl.execution.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.exceptions.*;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;
import dd.kms.hippodamus.impl.exceptions.Exceptions;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.Collection;

abstract class ConfigurationBuilderBaseImpl<C extends ExecutionCoordinatorImpl, B extends ExecutionConfigurationBuilder> implements ExecutionConfigurationBuilder
{
	final C						coordinator;

	private @Nullable String	name			= null;
	private int					taskType		= TaskType.REGULAR;
	private Collection<Handle>	dependencies	= ImmutableList.of();

	ConfigurationBuilderBaseImpl(C coordinator) {
		this.coordinator = coordinator;
	}

	abstract B getBuilder();

	@Override
	public B name(String name) {
		this.name = name;
		return getBuilder();
	}

	@Override
	public B taskType(int type) {
		Preconditions.checkArgument(coordinator.supportsTaskType(taskType), "No ExecutorService has been specified for task type " + taskType);
		this.taskType = type;
		return getBuilder();
	}

	@Override
	public B dependencies(Handle... dependencies) {
		return dependencies(ImmutableList.copyOf(dependencies));
	}

	@Override
	public B dependencies(Collection<Handle> dependencies) {
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
		return execute(Exceptions.asStoppable(runnable));
	}

	@Override
	public <T extends Throwable> Handle execute(StoppableExceptionalRunnable<T> runnable) {
		return execute(Exceptions.asCallable(runnable));
	}

	@Override
	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) {
		return execute(Exceptions.asStoppable(callable));
	}

	@Override
	public <V, T extends Throwable> ResultHandle<V> execute(StoppableExceptionalCallable<V, T> callable) {
		return coordinator.execute(callable, createConfiguration());
	}

	ExecutionConfiguration createConfiguration() {
		return new ExecutionConfiguration(name, taskType, dependencies);
	}
}
