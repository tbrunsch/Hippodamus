package dd.kms.hippodamus.aggregation;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.aggregation.Aggregators;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.ValueReference;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import static dd.kms.hippodamus.testUtils.TestUtils.BOOLEANS;

/**
 * This test focuses on computing the disjunction of two Boolean {@link Callable}s that are executed in parallel. A
 * naive approach would wait for both tasks to complete and then compute the result. The optimized approach uses short
 * circuit evaluation and stops one task if the other one completes with a positive result.
 */
class DisjunctionTest
{
	private static final Supplier<ExecutorService>	COMMON_FORK_JOIN_POOL_SUPPLIER		= TestUtils.createNamedInstance(Supplier.class, ForkJoinPool::commonPool, "common fork join pool");
	private static final Supplier<ExecutorService>	DEDICATED_EXECUTOR_SERVICE_SUPPLIER	= TestUtils.createNamedInstance(Supplier.class, Executors::newWorkStealingPool, "dedicated executor service");

	@ParameterizedTest(name = "computation of {0} || {1} with {2}")
	@MethodSource("getParameters")
	void testDisjunction(boolean operand1, boolean operand2, Supplier<ExecutorService> executorServiceSupplier) {
		ExecutorService executorService = executorServiceSupplier.get();

		ValueReference<Boolean> task1Started = new ValueReference<>(false);
		ValueReference<Boolean> task2Started = new ValueReference<>(false);

		TestEventManager eventManager = new TestEventManager();

		Aggregator<Boolean, Boolean> disjunctionAggregator = Aggregators.disjunction();
		Handle task1;
		Handle task2;
		AggregationCoordinatorBuilder<Boolean, Boolean> coordinatorBuilder = Coordinators
			.configureAggregationCoordinator(disjunctionAggregator)
			.executorService(TaskType.COMPUTATIONAL, executorService, true);
		try (AggregationCoordinator<Boolean, Boolean> coordinator = TestUtils.wrap(coordinatorBuilder.build(), eventManager)) {
			task1 = coordinator.aggregate(() -> simulateBooleanCallable(task2Started, operand1));
			task2 = coordinator.aggregate(() -> simulateBooleanCallable(task1Started, operand2));
			eventManager.onHandleEvent(task1, HandleState.STARTED, () -> task1Started.set(true));
			eventManager.onHandleEvent(task2, HandleState.STARTED, () -> task2Started.set(true));
		}
		boolean expectedResult = operand1 || operand2;

		/*
		 * Check result
		 */
		Assertions.assertEquals(expectedResult, disjunctionAggregator.getAggregatedValue(), "Wrong aggregated result");

		boolean task1Stopped = eventManager.encounteredEvent(task1, HandleState.STOPPED);
		boolean task2Stopped = eventManager.encounteredEvent(task2, HandleState.STOPPED);

		/*
		 * Check completion and stop state
		 */
		Assertions.assertTrue(task1.hasCompleted() && task2.hasCompleted(), "Both tasks should have completed");
		Assertions.assertFalse(task1Stopped && task2Stopped, "Not both tasks should have stopped");

		if (operand1 && operand2) {
			return;
		}

		if (operand1) {
			Assertions.assertFalse(task1Stopped, "Task 1 should not have stopped");
			Assertions.assertTrue(task2Stopped, "Task 2 should have stopped (short circuit evaluation)");
		} else if (operand2) {
			Assertions.assertTrue(task1Stopped, "Task 1 should have stopped (short circuit evaluation)");
			Assertions.assertFalse(task2Stopped, "Task 2 should not have stopped");
		} else {
			Assertions.assertFalse(task1Stopped, "Task 1 should not have stopped");
			Assertions.assertFalse(task2Stopped, "Task 2 should not have stopped");
		}
	}

	/*
	 * Stand-in for an arbitrarily complex Boolean callable
	 */
	private boolean simulateBooleanCallable(ValueReference<Boolean> startFlag, boolean result) {
		while (!startFlag.get());
		if (!result) {
			// give other task chance to return true
			for (int i = 0; i < 5; i++) {
				TestUtils.simulateWork(100);
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
			}
		}
		return result;
	}

	static List<Object[]> getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (boolean operand1 : BOOLEANS) {
			for (boolean operand2 : BOOLEANS) {
				for (Supplier<ExecutorService> executorServiceSupplier : Arrays.asList(COMMON_FORK_JOIN_POOL_SUPPLIER, DEDICATED_EXECUTOR_SERVICE_SUPPLIER)) {
					parameters.add(new Object[]{operand1, operand2, executorServiceSupplier});
				}
			}
		}
		return parameters;
	}
}
