package dd.kms.hippodamus.testUtils.execution.configuration;

import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.ExecutionController;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator;
import dd.kms.hippodamus.testUtils.exceptions.TestCallable;
import dd.kms.hippodamus.testUtils.exceptions.TestRunnable;

import java.util.Collection;

abstract class BaseTestConfigurationBuilder<B extends ExecutionConfigurationBuilder> implements ExecutionConfigurationBuilder
{
	final B							wrappedBuilder;
	final BaseTestCoordinator<?> testCoordinator;

	BaseTestConfigurationBuilder(B wrappedBuilder, BaseTestCoordinator<?> testCoordinator) {
		this.wrappedBuilder = wrappedBuilder;
		this.testCoordinator = testCoordinator;
	}

	abstract B getBuilder();

	@Override
	public B name(String name) {
		wrappedBuilder.name(name);
		return getBuilder();
	}

	@Override
	public B taskType(TaskType type) {
		wrappedBuilder.taskType(type);
		return getBuilder();
	}

	@Override
	public B dependencies(Handle... dependencies) {
		wrappedBuilder.dependencies(dependencies);
		return getBuilder();
	}

	@Override
	public B executionController(ExecutionController controller) {
		wrappedBuilder.executionController(controller);
		return getBuilder();
	}

	@Override
	public B dependencies(Collection<? extends Handle> dependencies) {
		wrappedBuilder.dependencies(dependencies);
		return getBuilder();
	}

	@Override
	public <T extends Throwable> Handle execute(ExceptionalRunnable<T> runnable) throws T {
		TestRunnable<T> testRunnable = new TestRunnable<>(testCoordinator, runnable);
		Handle handle = wrappedBuilder.execute(testRunnable);
		testRunnable.setHandle(handle);
		return handle;
	}

	@Override
	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) throws T {
		TestCallable<V, T> testCallable = new TestCallable<>(testCoordinator, callable);
		ResultHandle<V> handle = wrappedBuilder.execute(testCallable);
		testCallable.setHandle(handle);
		return handle;
	}
}
