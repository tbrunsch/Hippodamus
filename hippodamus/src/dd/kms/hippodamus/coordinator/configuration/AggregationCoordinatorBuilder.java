package dd.kms.hippodamus.coordinator.configuration;

import dd.kms.hippodamus.coordinator.AggregationCoordinator;

public interface AggregationCoordinatorBuilder<S, R> extends ExecutionCoordinatorBuilder<AggregationCoordinatorBuilder<S, R>>
{
	@Override
	AggregationCoordinator<S, R> build();
}
