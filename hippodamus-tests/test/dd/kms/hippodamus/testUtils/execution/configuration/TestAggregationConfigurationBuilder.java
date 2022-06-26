package dd.kms.hippodamus.testUtils.execution.configuration;

import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator;
import dd.kms.hippodamus.testUtils.exceptions.TestCallable;

public class TestAggregationConfigurationBuilder<S, R> extends BaseTestConfigurationBuilder<AggregationConfigurationBuilder<S, R>> implements AggregationConfigurationBuilder<S, R>
{
	public TestAggregationConfigurationBuilder(AggregationConfigurationBuilder<S, R> wrappedBuilder, BaseTestCoordinator<?> testCoordinator) {
		super(wrappedBuilder, testCoordinator);
	}

	@Override
	AggregationConfigurationBuilder<S, R> getBuilder() {
		return this;
	}

	@Override
	public <T extends Throwable> ResultHandle<S> aggregate(ExceptionalCallable<S, T> callable) throws T {
		TestCallable<S, T> testCallable = new TestCallable<>(testCoordinator, callable);
		ResultHandle<S> handle = wrappedBuilder.aggregate(testCallable);
		testCallable.setHandle(handle);
		return handle;
	}
}
