package dd.kms.hippodamus.aggregation;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.aggregation.Aggregators;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static dd.kms.hippodamus.testUtils.TestUtils.BOOLEANS;

/**
 * This test focuses on computing the disjunction of two Boolean {@link Callable}s that are executed
 * in parallel. A naive approach would wait for both tasks to complete and then compute the
 * result. The optimized approach uses short circuit evaluation and stops one task if the
 * other one completes with a positive result.
 */
class DisjunctionTest
{
	private static final Supplier<ExecutorService>	COMMON_FORK_JOIN_POOL_SUPPLIER		= TestUtils.createNamedInstance(Supplier.class, ForkJoinPool::commonPool, "common fork join pool");
	private static final Supplier<ExecutorService>	DEDICATED_EXECUTOR_SERVICE_SUPPLIER	= TestUtils.createNamedInstance(Supplier.class, Executors::newWorkStealingPool, "dedicated executor service");

	static Object getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (boolean operand1 : BOOLEANS) {
			for (boolean operand2 : BOOLEANS) {
				for (Supplier<ExecutorService> executorServiceSupplier : Arrays.asList(COMMON_FORK_JOIN_POOL_SUPPLIER, DEDICATED_EXECUTOR_SERVICE_SUPPLIER)) {
					parameters.add(new Object[]{ operand1, operand2, executorServiceSupplier });
				}
			}
		}
		return parameters;
	}

	@ParameterizedTest(name = "computation of {0} || {1} with {2}")
	@MethodSource("getParameters")
	void testDisjunction(boolean operand1, boolean operand2, Supplier<ExecutorService> executorServiceSupplier) {
		ExecutorService executorService = executorServiceSupplier.get();
		Aggregator<Boolean, Boolean> disjunctionAggregator = Aggregators.disjunction();
		Handle h1;
		Handle h2;
		Set<Integer> stoppedTaskIds = ConcurrentHashMap.newKeySet();
		AggregationCoordinatorBuilder<Boolean, Boolean> coordinatorBuilder = Coordinators
			.configureAggregationCoordinator(disjunctionAggregator)
			.executorService(TaskType.COMPUTATIONAL, executorService, true);
		try (AggregationCoordinator<Boolean, Boolean> coordinator = coordinatorBuilder.build()) {
			h1 = coordinator.aggregate(() -> simulateBooleanCallable(operand1, () -> stoppedTaskIds.add(1)));
			h2 = coordinator.aggregate(() -> simulateBooleanCallable(operand2, () -> stoppedTaskIds.add(2)));
		}
		boolean expectedResult = operand1 || operand2;

		/*
		 * Check result
		 */
		Assertions.assertEquals(expectedResult, disjunctionAggregator.getAggregatedValue(), "Wrong aggregated result");

		/*
		 * Check completion and stop state
		 */
		Assertions.assertTrue(h1.hasCompleted() && h2.hasCompleted(), "Both tasks should have completed");
		Assertions.assertFalse(stoppedTaskIds.contains(1) && stoppedTaskIds.contains(2), "Not both tasks should have stopped");

		if (operand1 && operand2) {
			return;
		}

		if (operand1) {
			Assertions.assertFalse(stoppedTaskIds.contains(1), "Task 1 should not have stopped");
			Assertions.assertTrue(stoppedTaskIds.contains(2), "Task 2 should have stopped (short circuit evaluation)");
		} else if (operand2) {
			Assertions.assertTrue(stoppedTaskIds.contains(1), "Task 1 should have stopped (short circuit evaluation)");
			Assertions.assertFalse(stoppedTaskIds.contains(2), "Task 2 should not have stopped");
		} else {
			Assertions.assertFalse(stoppedTaskIds.contains(1), "Task 1 should not have stopped");
			Assertions.assertFalse(stoppedTaskIds.contains(2), "Task 2 should not have stopped");
		}
	}

	/*
	 * Stand-in for an arbitrarily complex Boolean callable
	 */
	private boolean simulateBooleanCallable(boolean result, Runnable stoppedListener) {
		// give other tasks a chance to start
		TestUtils.simulateWork(500);
		if (!result) {
			// give other task chance to return true
			for (int i = 0; i < 5; i++) {
				TestUtils.simulateWork(100);
				if (Thread.interrupted()) {
					stoppedListener.run();
					// stop => return default value
					return false;
				}
			}
		}
		return result;
	}
}
