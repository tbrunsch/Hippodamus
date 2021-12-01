package dd.kms.hippodamus.api.aggregation;

public interface Aggregator<S, R>
{
	/**
	 * Aggregates the value
	 */
	void aggregate(S value);

	/**
	 * @return The accumulated value
	 */
	R getAggregatedValue();

	/**
	 * <p>This method is used for optimization (cf. short circuit evaluation).</p>
	 * <br>
	 * <p><b>Example 1:</b> Consider an {@code Aggregator} that determines the logic
	 * disjunction of a collection of Boolean values. The aggregation can be stopped
	 * if at least one of the values is true.</p>
	 * <p><b>Example 2:</b> Consider an {@code Aggregator} that determines whether the
	 * sum of a collection of non-negative numbers is larger than 10. The aggregation
	 * can be stopped if already the currently aggregated sum is larger than 10, so
	 * no further numbers have to be added.</p>
	 */
	boolean hasAggregationCompleted();
}
