package dd.kms.hippodamus.api.aggregation;

import java.util.function.BiFunction;
import java.util.function.Predicate;

public class Aggregators
{
	public static <S, R> Aggregator<S, R> createAggregator(R initValue, BiFunction<R, S, R> aggregationFunction) {
		return new dd.kms.hippodamus.impl.aggregation.DefaultAggregator<>(initValue, aggregationFunction, value -> false);
	}

	public static <S, R> Aggregator<S, R> createAggregator(R initValue, BiFunction<R, S, R> aggregationFunction, Predicate<R> finalValuePredicate) {
		return new dd.kms.hippodamus.impl.aggregation.DefaultAggregator<>(initValue, aggregationFunction, finalValuePredicate);
	}

	public static Aggregator<Boolean, Boolean> disjunction() {
		return createAggregator(false, (a, b) -> a || b, total -> total);
	}

	public static Aggregator<Boolean, Boolean> conjunction() {
		return createAggregator(true, (a, b) -> a && b, total -> !total);
	}
}
