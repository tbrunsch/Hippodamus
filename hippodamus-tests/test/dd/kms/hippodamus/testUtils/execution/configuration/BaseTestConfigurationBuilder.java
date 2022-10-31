package dd.kms.hippodamus.testUtils.execution.configuration;

import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.resources.Resource;
import dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator;
import dd.kms.hippodamus.testUtils.exceptions.TestCallable;
import dd.kms.hippodamus.testUtils.exceptions.TestRunnable;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class BaseTestConfigurationBuilder<B extends ExecutionConfigurationBuilder> implements ExecutionConfigurationBuilder
{
	final B							wrappedBuilder;
	final BaseTestCoordinator<?>	testCoordinator;
	Consumer<Handle>				handleConsumer	= handle -> {};

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
	public B dependencies(Collection<? extends Handle> dependencies) {
		wrappedBuilder.dependencies(dependencies);
		return getBuilder();
	}

	@Override
	public <T> B requiredResource(Resource<T> resource, Supplier<T> resourceShareSupplier) {
		wrappedBuilder.requiredResource(resource, resourceShareSupplier);
		return getBuilder();
	}

	@Override
	public B onHandleCreation(Consumer<Handle> handleConsumer) {
		this.handleConsumer = handleConsumer;
		return getBuilder();
	}

	@Override
	public <T extends Throwable> Handle execute(ExceptionalRunnable<T> runnable) throws T {
		TestRunnable<T> testRunnable = new TestRunnable<>(testCoordinator, runnable);
		return wrappedBuilder
			.onHandleCreation(handle -> {
				testRunnable.setHandle(handle);
				handleConsumer.accept(handle);
			})
			.execute(testRunnable);
	}

	@Override
	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) throws T {
		TestCallable<V, T> testCallable = new TestCallable<>(testCoordinator, callable);
		return wrappedBuilder
			.onHandleCreation(handle -> {
				testCallable.setHandle(handle);
				handleConsumer.accept(handle);
			})
			.execute(testCallable);
	}
}
