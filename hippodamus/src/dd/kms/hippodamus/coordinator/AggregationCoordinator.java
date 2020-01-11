package dd.kms.hippodamus.coordinator;

/**
 * Special {@link ExecutionCoordinator} for aggregating results of callables that are
 * evaluated in parallel.
 *
 * @param <S> The type of the values that are aggregated
 * @param <T> The type of the result value
 */
public interface AggregationCoordinator<S, T> extends AggregationManager<S>, ExecutionCoordinator
{
	/**
	 * Call this method to configure how a certain task has to be executed.
	 */
	AggregationConfigurationBuilder<S, T> configure();
}
