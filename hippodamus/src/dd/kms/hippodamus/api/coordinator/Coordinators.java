package dd.kms.hippodamus.api.coordinator;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;

/**
 * Utility class for creating {@link ExecutionCoordinator}s and {@link AggregationCoordinator}s.
 */
public class Coordinators
{
	/**
	 * Creates an {@link ExecutionCoordinator} with default settings.
	 */
	public static ExecutionCoordinator createExecutionCoordinator() {
		return configureExecutionCoordinator().build();
	}

	/**
	 * Returns an {@link ExecutionCoordinatorBuilder} for configuring and creating an {@link ExecutionCoordinator}.
	 */
	public static ExecutionCoordinatorBuilder configureExecutionCoordinator() {
		return new dd.kms.hippodamus.impl.coordinator.configuration.ExecutionCoordinatorBuilderImpl();
	}

	/**
	 * Creates an {@link AggregationCoordinator} with default settings and the specified {@link Aggregator}.
	 */
	public static <S, T> AggregationCoordinator<S, T> createAggregationCoordinator(Aggregator<S, T> aggregator) {
		return configureAggregationCoordinator(aggregator).build();
	}

	/**
	 * Returns an {@link AggregationCoordinatorBuilder} for configuring and creating an {@link AggregationCoordinator}
	 * with the specified {@link Aggregator}.
	 */
	public static <S, T> AggregationCoordinatorBuilder<S, T> configureAggregationCoordinator(Aggregator<S, T> aggregator) {
		return new dd.kms.hippodamus.impl.coordinator.configuration.AggregationCoordinatorBuilderImpl<>(aggregator);
	}
}
