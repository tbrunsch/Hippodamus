package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.resources.memory.CountableResource;
import dd.kms.hippodamus.resources.memory.DefaultCountableResource;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

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
		Assumptions.assumeTrue(TestUtils.getDefaultParallelism() >= 2);

		final int numResources = 2*HALF_NUMBER_OF_RESOURCES;

		List<CountableResource> resources = IntStream.range(0, numResources)
			.mapToObj(i -> new DefaultCountableResource("Resource " + (i+1), 1L))
			.collect(Collectors.toList());

		List<BitVector> bitVectors = getBitVectors(numResources, HALF_NUMBER_OF_RESOURCES);
		final int expectedNumTasks = binomialCoefficient(numResources, HALF_NUMBER_OF_RESOURCES);
		final int expectedParallelism = 2;

		Assertions.assertEquals(expectedNumTasks, bitVectors.size(), "Internal error: Unexpected number of tasks");

		TestUtils.waitForEmptyCommonForkJoinPool();

		StopWatch stopWatch = new StopWatch();

		BitVectorCollector bitVectorCollector = new BitVectorCollector();
		try (AggregationCoordinator<BitVector, List<BitVector>> coordinator = Coordinators.createAggregationCoordinator(bitVectorCollector)) {
			for (BitVector bitVector : bitVectors) {
				AggregationConfigurationBuilder<BitVector, List<BitVector>> taskConfiguration = coordinator.configure()
					.name(bitVector.toString());
				for (int pos = 0; pos < numResources; pos++) {
					if (bitVector.getBit(pos) == 1) {
						taskConfiguration.requiredResource(resources.get(pos), () -> 1L);
					}
				}
				taskConfiguration.aggregate(() -> executeTask(bitVector));
			}
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();
		long expectedDurationMs = (long) Math.ceil(1.0 * expectedNumTasks / expectedParallelism) * TASK_TIME_MS;
		TestUtils.assertTimeBounds(expectedDurationMs, PRECISION_MS, elapsedTimeMs);

		List<BitVector> executedBitVectors = bitVectorCollector.getAggregatedValue();
		Assertions.assertEquals(expectedNumTasks, executedBitVectors.size(), "Unexpected number of tasks");

		for (int i = 0; i < expectedNumTasks / 2; i++) {
			BitVector vector1 = executedBitVectors.get(2*i);
			BitVector vector2 = executedBitVectors.get(2*i + 1);
			Assertions.assertEquals(vector1.invert(), vector2, "Each pair of subsequent tasks must consist of complementary resource allocations");
		}
	}

	private BitVector executeTask(BitVector bitVector) {
		System.out.println(bitVector + " started");
		TestUtils.simulateWork(TASK_TIME_MS);
		System.out.println(bitVector + " finished");
		return bitVector;
	}

	private static List<BitVector> getBitVectors(int length, int hammingWeight) {
		List<BitVector> bitVectors = new ArrayList<>();
		if (length == 0) {
			bitVectors.add(new BitVector());
		}
		if (hammingWeight < length) {
			bitVectors.addAll(appendToAll(getBitVectors(length-1, hammingWeight), 0));
		}
		if (hammingWeight > 0) {
			bitVectors.addAll(appendToAll(getBitVectors(length-1, hammingWeight-1), 1));
		}
		return bitVectors;
	}

	private static List<BitVector> appendToAll(List<BitVector> bitVectors, int bit) {
		return bitVectors.stream()
			.map(bitVector -> bitVector.append(bit))
			.collect(Collectors.toList());
	}

	private static int binomialCoefficient(int n, int k) {
		return k == 0 || k == n
			? 1
			: binomialCoefficient(n-1, k-1) + binomialCoefficient(n-1, k);
	}

	private static class BitVector
	{
		private final int	bits;
		private final int	length;

		BitVector() {
			this(0, 0);
		}

		BitVector(int bits, int length) {
			this.bits = bits;
			this.length = length;
		}

		int getBit(int pos) {
			return (bits >> pos) & 1;
		}

		BitVector append(int bit) {
			return new BitVector(bits << 1 | bit, length + 1);
		}

		BitVector invert() {
			return new BitVector((1 << length) - (bits+1), length);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			BitVector bitVector = (BitVector) o;
			return bits == bitVector.bits &&
				length == bitVector.length;
		}

		@Override
		public int hashCode() {
			return Objects.hash(bits, length);
		}

		@Override
		public String toString() {
			return IntStream.range(0, length)
				.map(this::getBit)
				.mapToObj(String::valueOf)
				.collect(Collectors.joining());
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
