package dd.kms.hippodamus.api.coordinator;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;

public class Coordinators
{
	public static ExecutionCoordinator createExecutionCoordinator() {
		return configureExecutionCoordinator().build();
	}

	public static ExecutionCoordinatorBuilder configureExecutionCoordinator() {
		return new dd.kms.hippodamus.impl.coordinator.configuration.ExecutionCoordinatorBuilderImpl();
	}

	public static <S, T> AggregationCoordinator<S, T> createAggregationCoordinator(Aggregator<S, T> aggregator) {
		return configureAggregationCoordinator(aggregator).build();
	}

	public static <S, T> AggregationCoordinatorBuilder<S, T> configureAggregationCoordinator(Aggregator<S, T> aggregator) {
		return new dd.kms.hippodamus.impl.coordinator.configuration.AggregationCoordinatorBuilderImpl<>(aggregator);
	}
}
