package dd.kms.hippodamus.coordinator.configuration;

import dd.kms.hippodamus.coordinator.AggregationCoordinator;

public interface AggregationCoordinatorBuilder<S, T> extends ExecutionCoordinatorBuilder<AggregationCoordinatorBuilder<S, T>>
{
	@Override
	AggregationCoordinator<S, T> build();
}
