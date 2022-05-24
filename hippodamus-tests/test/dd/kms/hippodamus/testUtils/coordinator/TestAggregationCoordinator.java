package dd.kms.hippodamus.testUtils.coordinator;

import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.execution.configuration.TestAggregationConfigurationBuilder;

public class TestAggregationCoordinator<S, R> extends BaseTestCoordinator<AggregationCoordinator<S, R>> implements AggregationCoordinator<S, R>
{
	TestAggregationCoordinator(AggregationCoordinator<S, R> wrappedCoordinator, TestEventManager eventManager) {
		super(wrappedCoordinator, eventManager);
	}

	@Override
	public AggregationConfigurationBuilder<S, R> configure() {
		return new TestAggregationConfigurationBuilder(wrappedCoordinator.configure(), this);
	}

	@Override
	public <T extends Throwable> ResultHandle<S> aggregate(ExceptionalCallable<S, T> callable) throws T {
		return configure().aggregate(callable);
	}
}
