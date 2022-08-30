package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This test uses 6 resources and for each subset of these resources of size 3
 * one task that requires exactly these 3 resources. Only one task can hold a
 * share of a resource at the same time. Hence, at each point in time exactly
 * 2 tasks can run: An arbitrary task and its complementary task, i.e. the task
 * that uses exactly the 3 other resources.
 */
public class MultipleResourcesTest
{
	private static final long	TASK_TIME_MS				= 500;
	private static final long	PRECISION_MS				= 300;
	private static final int	HALF_NUMBER_OF_RESOURCES	= 3;

	@Test
	public void testMultipleResources() {
		Assume.assumeTrue(TestUtils.getDefaultParallelism() >= 2);

		final int numResources = 2*HALF_NUMBER_OF_RESOURCES;

		List<CountableResource> resources = IntStream.range(0, numResources)
			.mapToObj(i -> new DefaultCountableResource("Resource " + (i+1), 1L))
			.collect(Collectors.toList());

		List<BitVector> bitVectors = getBitVectors(numResources, HALF_NUMBER_OF_RESOURCES);
		final int expectedNumTasks = binomialCoefficient(numResources, HALF_NUMBER_OF_RESOURCES);
		final int expectedParallelism = 2;

		Assert.assertEquals("Internal error: Unexpected number of tasks", expectedNumTasks, bitVectors.size());

		TestUtils.waitForEmptyCommonForkJoinPool();

		StopWatch stopWatch = new StopWatch();

		BitVectorCollector bitVectorCollector = new BitVectorCollector();
		try (AggregationCoordinator<BitVector, List<BitVector>> coordinator = Coordinators.createAggregationCoordinator(bitVectorCollector)) {
			for (BitVector bitVector : bitVectors) {
				AggregationConfigurationBuilder<BitVector, List<BitVector>> taskConfiguration = coordinator.configure();
				for (int bit = 0; bit < numResources; bit++) {
					if (bitVector.isBitSet(bit)) {
						taskConfiguration.requiredResource(resources.get(bit), () -> 1L);
					}
				}
				taskConfiguration.aggregate(() -> executeTask(bitVector));
			}
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();
		long expectedDurationMs = (long) Math.ceil(1.0 * expectedNumTasks / expectedParallelism) * TASK_TIME_MS;
		TestUtils.assertTimeBounds(expectedDurationMs, PRECISION_MS, elapsedTimeMs);

		List<BitVector> executedBitVectors = bitVectorCollector.getAggregatedValue();
		Assert.assertEquals("Unexpected number of tasks", expectedNumTasks, executedBitVectors.size());

		for (int i = 0; i < expectedNumTasks / 2; i++) {
			BitVector vector1 = executedBitVectors.get(2*i);
			BitVector vector2 = executedBitVectors.get(2*i + 1);
			Assert.assertEquals("Each pair of subsequent tasks must consist of complementary resource allocations", vector1.invert(), vector2);
		}
	}

	private BitVector executeTask(BitVector bitVector) {
		TestUtils.simulateWork(TASK_TIME_MS);
		return bitVector;
	}

	private static List<BitVector> getBitVectors(int length, int hammingWeight) {
		List<BitVector> bitVectors = new ArrayList<>();
		if (length == 0) {
			bitVectors.add(new BitVector());
		}
		if (hammingWeight < length) {
			bitVectors.addAll(appendToAll(getBitVectors(length-1, hammingWeight), false));
		}
		if (hammingWeight > 0) {
			bitVectors.addAll(appendToAll(getBitVectors(length-1, hammingWeight-1), true));
		}
		return bitVectors;
	}

	private static List<BitVector> appendToAll(List<BitVector> bitVectors, boolean bitValue) {
		return bitVectors.stream()
			.map(bitVector -> bitVector.append(bitValue))
			.collect(Collectors.toList());
	}

	private static int binomialCoefficient(int n, int k) {
		return k == 0 || k == n
			? 1
			: binomialCoefficient(n-1, k-1) + binomialCoefficient(n-1, k);
	}

	private static class BitVector
	{
		private final List<Boolean> bits;

		BitVector() {
			this(new ArrayList<>());
		}

		BitVector(List<Boolean> bits) {
			this.bits = bits;
		}

		boolean isBitSet(int bit) {
			return bits.get(bit);
		}

		BitVector append(boolean bitValue) {
			List<Boolean> bits = new ArrayList<>(this.bits);
			bits.add(bitValue);
			return new BitVector(bits);
		}

		BitVector invert() {
			List<Boolean> bits = this.bits.stream().map(b -> !b).collect(Collectors.toList());
			return new BitVector(bits);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			BitVector bitVector = (BitVector) o;
			return Objects.equals(bits, bitVector.bits);
		}

		@Override
		public int hashCode() {
			return Objects.hash(bits);
		}
	}

	private static class BitVectorCollector implements Aggregator<BitVector, List<BitVector>>
	{
		private final List<BitVector>	bitVectors = new ArrayList<>();

		@Override
		public void aggregate(BitVector bitVector) {
			bitVectors.add(bitVector);
		}

		@Override
		public List<BitVector> getAggregatedValue() {
			return bitVectors;
		}

		@Override
		public boolean hasAggregationCompleted() {
			return false;
		}
	}
}
