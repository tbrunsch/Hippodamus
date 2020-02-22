package dd.kms.hippodamus.coordinator.configuration;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.coordinator.AggregationCoordinatorImpl;

public class AggregationCoordinatorBuilderImpl<S, R>
	extends ExecutionCoordinatorBuilderImpl<AggregationCoordinatorBuilder<S, R>>
	implements AggregationCoordinatorBuilder<S, R>
{
	private final Aggregator<S, R> aggregator;

	public AggregationCoordinatorBuilderImpl(Aggregator<S, R> aggregator) {
		this.aggregator = aggregator;
	}

	@Override
	public AggregationCoordinator<S, R> build() {
		return new AggregationCoordinatorImpl<>(aggregator, createConfiguration());
	}
}
