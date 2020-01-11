package dd.kms.hippodamus.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalRunnable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;

import javax.annotation.Nullable;
import java.util.Collection;

class ExecutionConfigurationBuilderImpl<T extends ExecutionCoordinatorImpl, B extends ExecutionConfigurationBuilder<B>> implements ExecutionConfigurationBuilder<B>
{
	final T						coordinator;

	private @Nullable String	name			= null;
	private int					taskType		= TaskType.REGULAR;
	private Collection<Handle>	dependencies	= ImmutableList.of();

	ExecutionConfigurationBuilderImpl(T coordinator) {
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
		return coordinator.execute(runnable, createConfiguration());
	}

	@Override
	public <E extends Exception> Handle execute(StoppableExceptionalRunnable<E> runnable) {
		return coordinator.execute(runnable, createConfiguration());
	}

	@Override
	public <V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable) {
		return coordinator.execute(callable, createConfiguration());
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
