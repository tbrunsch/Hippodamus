package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.coordinator.configuration.AggregationCoordinatorBuilderImpl;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilderImpl;

public class Coordinators
{
	public static ExecutionCoordinator createExecutionCoordinator() {
		return configureExecutionCoordinator().build();
	}

	public static ExecutionCoordinatorBuilder<?> configureExecutionCoordinator() {
		return new ExecutionCoordinatorBuilderImpl<>();
	}

	public static <S, T> AggregationCoordinator<S, T> createAggregationCoordinator(Aggregator<S, T> aggregator) {
		return configureAggregationCoordinator(aggregator).build();
	}

	public static <S, T> AggregationCoordinatorBuilder<S, T> configureAggregationCoordinator(Aggregator<S, T> aggregator) {
		return new AggregationCoordinatorBuilderImpl<>(aggregator);
	}
}
