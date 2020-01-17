package dd.kms.hippodamus.coordinator.configuration;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.coordinator.AggregationCoordinatorImpl;

public class AggregationCoordinatorBuilderImpl<S, T>
	extends ExecutionCoordinatorBuilderImpl<AggregationCoordinatorBuilder<S, T>>
	implements AggregationCoordinatorBuilder<S, T>
{
	private final Aggregator<S, T> aggregator;

	public AggregationCoordinatorBuilderImpl(Aggregator<S, T> aggregator) {
		this.aggregator = aggregator;
	}

	@Override
	public AggregationCoordinator<S, T> build() {
		return new AggregationCoordinatorImpl<>(aggregator, createConfiguration());
	}
}
