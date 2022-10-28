package dd.kms.hippodamus.impl.execution.configuration;

import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.impl.coordinator.AggregationCoordinatorImpl;

public class AggregationConfigurationBuilderImpl<S, R>
	extends ConfigurationBuilderBase<AggregationCoordinatorImpl<S, R>, AggregationConfigurationBuilder<S, R>>
	implements AggregationConfigurationBuilder<S, R>
{
	public AggregationConfigurationBuilderImpl(AggregationCoordinatorImpl<S, R> coordinator) {
		super(coordinator);
	}

	@Override
	AggregationConfigurationBuilder<S, R> getBuilder() {
		return this;
	}

	@Override
	public <T extends Throwable> ResultHandle<S> aggregate(ExceptionalCallable<S, T> callable) throws T {
		return coordinator.aggregate(callable, createConfiguration(false));
	}
}
