package dd.kms.hippodamus.aggregation;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.aggregation.Aggregators;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.events.TestEvents;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static dd.kms.hippodamus.api.coordinator.TaskType.BLOCKING;

/**
 * This test simulates the element-wise comparison of two objects. The elements of one object are, e.g., loaded from a
 * file (blocking task), whereas the elements of the other object are generated (computational task). The corresponding
 * elements are then compared (computational task).<br>
 * <br>
 * The total comparison result is the conjunction of the element comparison results. If we would use a single task for
 * loading one element from file, generating the other element and comparing them, then this would be a scenario similar
 * to what is covered by {@link DisjunctionTest}. However, these complex tasks should then be classified as blocking
 * tasks. By default, blocking tasks are executed sequentially. This test focuses on increasing parallelism by loading
 * and generating elements in parallel and by performing the comparison while the next elements are loaded and generated.
 * <br>
 * <br>
 * <b>Test setup:</b> We assume that the objects we want to compare consist of N elements. For each element, we create
 * the following task:
 * <ul>
 *     <li>a blocking task that loads the element,</li>
 *     <li>a computational task that generates the other element, and </li>
 *     <li>a computational task that compares both elements.</li>
 * </ul>
 * The comparison task depends on the loading and the generation task. Since blocking tasks a executed by a single
 * thread, the loading tasks will be processed sequentially, whereas the generation tasks might be executed in
 * parallel.<br>
 * <br>
 * Assume that we have n pairs of elements that we have to compare until we know the comparison result of the whole
 * object, i.e., n is the first element index (1-based) for which both objects differ, if they differ, or n = N if both
 * objects are equal. We chose the loading time of each element in such a way that it is larger than the generation time
 * plus the comparison time of one element. This guarantees that the total time until the comparison result is determined
 * is dominated by the total time of the n loading tasks. After the n'th element has been loaded, only the n'th
 * comparison task has to be executed until the comparison result is known. Hence, the total time will be at least and
 * approximately<br>
 * <br>
 *       {@code n*LOAD_TIME_MS + COMPARISON_TIME_MS}.<br>
 * <br>
 * <b>Background:</b> This test may seem constructed at first glance, but it covers a real use case of Hippodamus: It is
 * an integration test in which a snapshot of a test system has been taken a while ago and is now compared against a new
 * snapshot of the test system after code changes and a restart of the system under exactly the same conditions.
 */
class ElementwiseComparisonTest
{
	private static final long	LOAD_TIME_MS		= 2000;
	private static final long	GENERATION_TIME_MS	= 800;
	private static final long	COMPARISON_TIME_MS	= 1000;
	private static final long	PRECISION_MS		= 500;

	private static final int	NUM_ELEMENTS		= 3;

	static {
		assert LOAD_TIME_MS >= GENERATION_TIME_MS + COMPARISON_TIME_MS : "Test assumptions are not met";
	}

	/**
	 * @param numElements number of elements the objects to compare consist of
	 * @param deviatingElementIndex the index of the first element both objects differ, or -1 if the objects are equal
	 */
	@ParameterizedTest(name = "number of elements: {0}, deviating element index: {1}")
	@MethodSource("getParameters")
	void testComparison(int numElements, int deviatingElementIndex) {
		TestUtils.waitForEmptyCommonForkJoinPool();
		Aggregator<Boolean, Boolean> conjunctionAggregator = Aggregators.conjunction();
		boolean objectsEqual = deviatingElementIndex == -1;
		TestEventManager eventManager = new TestEventManager();
		AggregationCoordinatorBuilder<Boolean, Boolean> coordinatorBuilder = Coordinators
			.configureAggregationCoordinator(conjunctionAggregator);
		try (AggregationCoordinator<Boolean, Boolean> coordinator = TestUtils.wrap(coordinatorBuilder.build(), eventManager)) {
			for (int i = 0; i < numElements; i++) {
				int index = i;
				boolean elementsEqual = i != deviatingElementIndex;
				ResultHandle<Integer> loadElementTask = coordinator.configure()
					.name("Load element " + index)
					.taskType(BLOCKING)
					.execute(() -> simulateLoadElement(index));
				ResultHandle<Integer> generateElementTask = coordinator.configure()
					.name("Generate element " + index)
					.execute(() -> simulateGenerateElement(index, elementsEqual));
				coordinator.configure()
					.name("Compare element " + index)
					.dependencies(loadElementTask, generateElementTask)
					.aggregate(() -> compareElements(loadElementTask.get(), generateElementTask.get()));
			}
		}

		Assertions.assertEquals(objectsEqual, conjunctionAggregator.getAggregatedValue(), "Wrong aggregated result");

		checkTimeConstraints(eventManager.getElapsedTimeMs(TestEvents.COORDINATOR_CLOSED), numElements, deviatingElementIndex);
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
	private void checkTimeConstraints(long elapsedTimeMs, int numElements, int deviatingElementIndex) {
		int numRequiredTasksForComparison = deviatingElementIndex == -1
			? numElements
			: deviatingElementIndex + 1;
		long waitForFurtherLoadTimeMs = numRequiredTasksForComparison < numElements ? LOAD_TIME_MS : 0;
		long lowerBoundMs = numRequiredTasksForComparison * LOAD_TIME_MS + COMPARISON_TIME_MS;
		TestUtils.assertTimeBounds(lowerBoundMs, waitForFurtherLoadTimeMs + PRECISION_MS, elapsedTimeMs);
	}

	static Object getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (int deviatingElementIndex = -1; deviatingElementIndex < NUM_ELEMENTS; deviatingElementIndex++) {
			parameters.add(new Object[]{NUM_ELEMENTS, deviatingElementIndex});
		}
		return parameters;
	}
}
