package dd.kms.hippodamus.execution.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.coordinator.ExecutionCoordinatorImpl;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.exceptions.*;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;

import javax.annotation.Nullable;
import java.util.Collection;

public class ExecutionConfigurationBuilderImpl<T extends ExecutionCoordinatorImpl, B extends ExecutionConfigurationBuilder<B>> implements ExecutionConfigurationBuilder<B>
{
	final T						coordinator;

	private @Nullable String	name			= null;
	private int					taskType		= TaskType.REGULAR;
	private Collection<Handle>	dependencies	= ImmutableList.of();

	public ExecutionConfigurationBuilderImpl(T coordinator) {
		this.coordinator = coordinator;
	}

	@Override
	public B name(String name) {
		this.name = name;
		return getBuilder();
	}

	@Override
	public B taskType(int type) {
		this.taskType = type;
		return getBuilder();
	}

	@Override
	public B dependencies(Handle... dependencies) {
		return dependencies(ImmutableList.copyOf(dependencies));
	}

	@Override
	public B dependencies(Collection<Handle> dependencies) {
		Preconditions.checkArgument(dependencies.stream().allMatch(dependency -> dependency.getExecutionCoordinator() == coordinator),
			"Only dependencies to tasks of the same execution coordinator are allowed");
		this.dependencies = ImmutableList.copyOf(dependencies);
		return getBuilder();
	}

	@Override
	public <E extends Exception> Handle execute(ExceptionalRunnable<E> runnable) {
		return execute(Exceptions.asStoppable(runnable));
	}

	@Override
	public <E extends Exception> Handle execute(StoppableExceptionalRunnable<E> runnable) {
		return execute(Exceptions.asCallable(runnable));
	}

	@Override
	public <V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable) {
		return execute(Exceptions.asStoppable(callable));
	}

	@Override
	public <V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable) {
		return coordinator.execute(callable, createConfiguration());
	}

	private B getBuilder() {
		return (B) this;
	}

	ExecutionConfiguration createConfiguration() {
		return new ExecutionConfiguration(name, taskType, dependencies);
	}
}
