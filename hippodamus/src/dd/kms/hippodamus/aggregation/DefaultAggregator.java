package dd.kms.hippodamus.aggregation;

import java.util.function.BiFunction;
import java.util.function.Predicate;

class DefaultAggregator<S, T> implements Aggregator<S, T>
{
	private final BiFunction<T, S, T>	aggregationFunction;
	private final Predicate<T>			finalValuePredicate;

	private T							aggregatedValue;

	DefaultAggregator(T initValue, BiFunction<T, S, T> aggregationFunction, Predicate<T> finalValuePredicate) {
		this.aggregatedValue = initValue;
		this.aggregationFunction = aggregationFunction;
		this.finalValuePredicate = finalValuePredicate;
	}

	@Override
	public void aggregate(S value) {
		aggregatedValue = aggregationFunction.apply(aggregatedValue, value);
	}

	@Override
	public T getAggregatedValue() {
		return aggregatedValue;
	}

	@Override
	public boolean hasAggregationCompleted() {
		return finalValuePredicate.test(aggregatedValue);
	}
}
