package dd.kms.hippodamus.aggregation;

import dd.kms.hippodamus.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

import static dd.kms.hippodamus.coordinator.TaskType.IO;
import static dd.kms.hippodamus.testUtils.TestUtils.BOOLEANS;

/**
 * This test simulates the element-wise comparison of two objects. The elements of one object
 * are, e.g., loaded from a file (IO task), whereas the elements of the other object are
 * generated (regular task). The corresponding elements are then compared (regular task).<br/>
 * <br/>
 * The total comparison result is the conjunction of the element comparison results. If we
 * would use a single task for loading one element from file, generating the other element
 * and comparing them, then this would be a scenario similar to what is covered by
 * {@link DisjunctionTest}. However, these complex tasks should then be classified as IO tasks.
 * By default, IO tasks are executed sequentially. This test focuses on increasing parallelism
 * by loading and generating elements in parallel and by performing the comparison while the
 * next elements are loaded and generated.
 */
@RunWith(Parameterized.class)
public class ElementwiseComparisonTest
{
	/**
	 * The ratio between IO and non-IO time might not be realistic, but it is important that the
	 * time constraints are met in all but a very few cases, but meeting the constraints still
	 * indicates the correctness of the implementation.
	 *
	 * Calculation: Assume that we have n pairs of elements that we have to compare until we know
	 * the comparison result of the whole object. If we have at least two idle cores and process
	 * tasks as parallel as possible ("load" in a single thread, "generate" in another thread,
	 * "compare" after "load" + "generate"), then the IO thread will load one element after the
	 * other without interruptions. During that time, the generation of all elements and the
	 * comparision of all but the last element pair can be done in another thread. Only the
	 * comparison of the last element pair happens after loading the last element. Hence, the total
	 * time will be at least (and approximately)<br/>
	 * <br/>
	 *       {@code n*LOAD_TIME_MS + COMPARISON_TIME_MS}.<br/>
	 * <br/>
	 * This motivates the following time constraints that we test for:<br/>
	 * <br/>
	 *       {@code L <= time <= L+PRECISION_MS} for<br/>
	 * <br/>
	 * 		{@code L = n*LOAD_TIME_MS + COMPARISON_TIME_MS}
	 */
	private static final long	LOAD_TIME_MS		= 2000;
	private static final long	GENERATION_TIME_MS	= 800;
	private static final long	COMPARISON_TIME_MS	= 1000;
	private static final long	PRECISION_MS		= 500;

	@Parameterized.Parameters(name = "comparison results: {0}, {1}, {2}")
	public static Object getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (boolean cmp1 : BOOLEANS) {
			for (boolean cmp2 : BOOLEANS) {
				for (boolean cmp3 : BOOLEANS) {
					parameters.add(new Object[]{ cmp1, cmp2, cmp3 });
				}
			}
		}
		return parameters;
	}

	private final boolean[] elementComparisonResults;

	public ElementwiseComparisonTest(boolean elementComparisonResult1, boolean elementComparisonResult2, boolean elementComparisonResult3) {
		this.elementComparisonResults = new boolean[]{ elementComparisonResult1, elementComparisonResult2, elementComparisonResult3 };
	}

	@Test
	public void testComparison() {
		TestUtils.waitForEmptyCommonForkJoinPool();
		Aggregator<Boolean, Boolean> conjunctionAggregator = Aggregators.conjunction();
		boolean expectedResult = true;
		StopWatch stopWatch = new StopWatch();
		try (AggregationCoordinator<Boolean, Boolean> coordinator = Coordinators.createAggregationCoordinator(conjunctionAggregator)) {
			for (int i = 0; i < elementComparisonResults.length; i++) {
				int index = i;
				boolean equal = elementComparisonResults[i];
				expectedResult &= equal;
				ResultHandle<Integer> loadElementHandle = coordinator.configure()
					.name("Load element " + index)
					.taskType(IO)
					.execute(() -> simulateLoadElement(index));
				ResultHandle<Integer> generateElementHandle = coordinator.configure()
					.name("Generate element " + index)
					.execute(() -> simulateGenerateElement(index, equal));
				coordinator.configure()
					.name("Compare element " + index)
					.dependencies(loadElementHandle, generateElementHandle)
					.aggregate(() -> compareElements(loadElementHandle.get(), generateElementHandle.get()));
			}
		}
		if (TestUtils.getPotentialParallelism() < 2) {
			// We do not require time constraints to be met with only 1 processor
			System.out.println("Skipped checking time constraints");
		} else {
			checkTimeConstraints(stopWatch.getElapsedTimeMs());
		}

		Assert.assertTrue(conjunctionAggregator.getAggregatedValue() == expectedResult);
	}

	private int simulateLoadElement(int index) {
		TestUtils.simulateWork(LOAD_TIME_MS);
		return getElementFor(index);
	}

	private int simulateGenerateElement(int index, boolean sameAsLoadedElement) {
		TestUtils.simulateWork(GENERATION_TIME_MS);
		int element = getElementFor(index);
		return sameAsLoadedElement ? element : element + 1;
	}

	private boolean compareElements(int loadedElement, int generatedElement) {
		TestUtils.simulateWork(COMPARISON_TIME_MS);
		return loadedElement == generatedElement;
	}

	private int getElementFor(int index) {
		return index + 1;
	}

	/*
	 * See comment on LOAD_TIME_MS, GENERATION_TIME_MS, and COMPARISON_TIME_MS
	 */
	private void checkTimeConstraints(long elapsedTimeMs) {
		int numRequiredTasksForComparison = 1;
		for (int i = 0; i < elementComparisonResults.length - 1; i++) {
			if (!elementComparisonResults[i]) {
				break;
			}
			numRequiredTasksForComparison++;
		}

		long lowerBoundMs = numRequiredTasksForComparison * LOAD_TIME_MS + COMPARISON_TIME_MS;
		long upperBoundMs = lowerBoundMs + PRECISION_MS;
		TestUtils.assertTimeLowerBound(lowerBoundMs, elapsedTimeMs);
		TestUtils.assertTimeUpperBound(upperBoundMs, elapsedTimeMs);
	}
}
