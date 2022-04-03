package dd.kms.hippodamus.api.aggregation;

import dd.kms.hippodamus.impl.aggregation.AggregatorImpl;

import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Utility class for creating an {@link Aggregator}.
 */
public class Aggregators
{
	/**
	 * Creates an {@link Aggregator} with the given initial value and the update function
	 * {@code (current aggregated value, value) -> new aggregated value} and without
	 * short circuit evaluation.
	 */
	public static <S, R> Aggregator<S, R> createAggregator(R initValue, BiFunction<R, S, R> aggregationFunction) {
		return createAggregator(initValue, aggregationFunction, value -> false);
	}

	/**
	 * Creates an {@link Aggregator} like {@link #createAggregator(Object, BiFunction), but you
	 * can specify a predicate that returns, depending on the current aggregated value, whether
	 * short circuit evaluation can be applied.
	 */
	public static <S, R> Aggregator<S, R> createAggregator(R initValue, BiFunction<R, S, R> aggregationFunction, Predicate<R> finalValuePredicate) {
		return new AggregatorImpl<>(initValue, aggregationFunction, finalValuePredicate);
	}

	/**
	 * Returns an aggregator that computes the logical disjunction of boolean values and allows
	 * short circuit evaluation if at least one of the values considered so was true.
	 */
	public static Aggregator<Boolean, Boolean> disjunction() {
		return createAggregator(false, (a, b) -> a || b, total -> total);
	}

	/**
	 * Returns an aggregator that computes the logical conjunction of boolean values and allows
	 * short circuit evaluation if at least one of the values considered so was false.
	 */
	public static Aggregator<Boolean, Boolean> conjunction() {
		return createAggregator(true, (a, b) -> a && b, total -> !total);
	}
}
