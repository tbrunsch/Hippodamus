package dd.kms.hippodamus.impl.coordinator.configuration;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.impl.coordinator.AggregationCoordinatorImpl;

public class AggregationCoordinatorBuilderImpl<S, R>
	extends CoordinatorBuilderBaseImpl<AggregationCoordinatorBuilder<S, R>>
	implements AggregationCoordinatorBuilder<S, R>
{
	private final Aggregator<S, R> aggregator;

	public AggregationCoordinatorBuilderImpl(Aggregator<S, R> aggregator) {
		this.aggregator = aggregator;
	}

	@Override
	AggregationCoordinatorBuilder<S, R> getBuilder() {
		return this;
	}

	@Override
	public AggregationCoordinator<S, R> build() {
		return new AggregationCoordinatorImpl<>(aggregator, createConfiguration());
	}
}
