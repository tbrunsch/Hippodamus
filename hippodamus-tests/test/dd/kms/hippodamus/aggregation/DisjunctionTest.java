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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
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
		AggregationCoordinatorBuilder<Boolean, Boolean> coordinatorBuilder = Coordinators
			.configureAggregationCoordinator(disjunctionAggregator)
			.executorService(TaskType.COMPUTATIONAL, executorService, true);
		try (AggregationCoordinator<Boolean, Boolean> coordinator = coordinatorBuilder.build()) {
			h1 = coordinator.aggregate(() -> simulateBooleanCallable(operand1));
			h2 = coordinator.aggregate(() -> simulateBooleanCallable(operand2));
		}
		boolean expectedResult = operand1 || operand2;

		/*
		 * Check completion and stop state
		 */
		if (operand1) {
			if (operand2) {
				Assertions.assertTrue(h1.hasCompleted() || h2.hasCompleted(), "At least one of the tasks should have completed");
			} else {
				Assertions.assertTrue(h1.hasCompleted(), "Task 1 should have completed");
				Assertions.assertFalse(h2.hasCompleted(), "Task 2 should not have completed (short circuit evaluation)");

				Assertions.assertTrue(h2.hasStopped(), "Task 2 should have been stopped (short circuit evaluation)");
			}
		} else {
			if (operand2) {
				Assertions.assertFalse(h1.hasCompleted(), "Task 1 should not have completed (short circuit evaluation)");
				Assertions.assertTrue(h2.hasCompleted(), "Task 2 should have completed");

				Assertions.assertTrue(h1.hasStopped(), "Task 1 should have been stopped (short circuit evaluation)");
			} else {
				Assertions.assertTrue(h1.hasCompleted(), "Task 1 should have completed");
				Assertions.assertTrue(h2.hasCompleted(), "Task 2 should have completed");

				Assertions.assertFalse(h1.hasStopped(), "Task 1 must not have stopped");
				Assertions.assertFalse(h2.hasStopped(), "Task 2 must not have stopped");
			}
		}

		/*
		 * Check result
		 */
		Assertions.assertEquals(expectedResult, disjunctionAggregator.getAggregatedValue(), "Wrong aggregated result");
	}

	/*
	 * Stand-in for an arbitrarily complex Boolean callable
	 */
	private boolean simulateBooleanCallable(boolean result) {
		if (!result) {
			// give other task chance to return true
			TestUtils.simulateWork(500);
		}
		return result;
	}
}
