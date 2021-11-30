package dd.kms.hippodamus.api.coordinator;

import dd.kms.hippodamus.api.execution.AggregationManager;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;

/**
 * Special {@link ExecutionCoordinator} for aggregating results of callables that are
 * evaluated in parallel.
 *
 * @param <S> The type of the values that are aggregated
 * @param <R> The type of the result value
 */
public interface AggregationCoordinator<S, R> extends AggregationManager<S>, ExecutionCoordinator
{
	/**
	 * Call this method to configure how a certain task has to be executed.
	 */
	AggregationConfigurationBuilder<S, R> configure();
}
