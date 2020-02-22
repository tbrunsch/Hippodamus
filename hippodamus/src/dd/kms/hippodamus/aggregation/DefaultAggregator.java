package dd.kms.hippodamus.aggregation;

import java.util.function.BiFunction;
import java.util.function.Predicate;

class DefaultAggregator<S, R> implements Aggregator<S, R>
{
	private final BiFunction<R, S, R>	aggregationFunction;
	private final Predicate<R>			finalValuePredicate;

	private volatile R					aggregatedValue;

	DefaultAggregator(R initValue, BiFunction<R, S, R> aggregationFunction, Predicate<R> finalValuePredicate) {
		this.aggregatedValue = initValue;
		this.aggregationFunction = aggregationFunction;
		this.finalValuePredicate = finalValuePredicate;
	}

	@Override
	public void aggregate(S value) {
		aggregatedValue = aggregationFunction.apply(aggregatedValue, value);
	}

	@Override
	public R getAggregatedValue() {
		return aggregatedValue;
	}

	@Override
	public boolean hasAggregationCompleted() {
		return finalValuePredicate.test(aggregatedValue);
	}
}
