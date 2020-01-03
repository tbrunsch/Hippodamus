package dd.kms.hippodamus.aggregation;

import dd.kms.hippodamus.common.NamedInstances;
import dd.kms.hippodamus.common.ReadableValue;
import dd.kms.hippodamus.coordinator.AggregatingTaskCoordinator;
import dd.kms.hippodamus.coordinator.ExecutorServiceIds;
import dd.kms.hippodamus.coordinator.TaskCoordinators;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import static dd.kms.hippodamus.coordinator.ExecutorServiceIds.REGULAR;

/**
 * This test focuses on computing the disjunction of two Boolean {@link Callable}s that are executed
 * in parallel. A naive approach would wait for both tasks to complete and then compute the
 * result. The optimized approach uses short circuit evaluation and stops one task if the
 * other one completes with a positive result.
 */
@RunWith(Parameterized.class)
public class DisjunctionTest
{
	private static final Supplier<ExecutorService>	COMMON_FORK_JOIN_POOL_SUPPLIER		= NamedInstances.createNamedInstance(Supplier.class, ForkJoinPool::commonPool, "common fork join pool");
	private static final Supplier<ExecutorService>	DEDICATED_EXECUTOR_SERVICE_SUPPLIER	= NamedInstances.createNamedInstance(Supplier.class, Executors::newWorkStealingPool, "dedicated executor service");

	@Parameterized.Parameters(name = "computation of {0} || {1} with {2}")
	public static Object getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (Boolean operand1 : Arrays.asList(false, true)) {
			for (Boolean operand2 : Arrays.asList(false, true)) {
				for (Supplier<ExecutorService> executorServiceSupplier : Arrays.asList(COMMON_FORK_JOIN_POOL_SUPPLIER, DEDICATED_EXECUTOR_SERVICE_SUPPLIER)) {
					parameters.add(new Object[]{ operand1, operand2, executorServiceSupplier });
				}
			}
		}
		return parameters;
	}

	private final boolean 					operand1;
	private final boolean 					operand2;
	private final Supplier<ExecutorService>	executorServiceSupplier;

	public DisjunctionTest(boolean operand1, boolean operand2, Supplier<ExecutorService> executorServiceSupplier) {
		this.operand1 = operand1;
		this.operand2 = operand2;
		this.executorServiceSupplier = executorServiceSupplier;
	}

	@Test
	public void testDisjunction() {
		ExecutorService executorService = executorServiceSupplier.get();
		Aggregator<Boolean, Boolean> disjunctionAggregator = Aggregators.disjunction();
		ReadableValue<Boolean> disjunctionValue;
		Handle h1;
		Handle h2;
		try (AggregatingTaskCoordinator<Boolean, Boolean> coordinator = TaskCoordinators.createAggregatingTaskCoordinator(disjunctionAggregator, executorService)) {
			disjunctionValue = coordinator.getResultValue();
			h1 = coordinator.aggregate(() -> simulateBooleanCallable(operand1), REGULAR);
			h2 = coordinator.aggregate(() -> simulateBooleanCallable(operand2), REGULAR);
		} catch (InterruptedException e) {
			Assert.fail("Interrupted exception");
			return;
		}

		boolean expectedResult = operand1 || operand2;

		/*
		 * Check completion and stop state
		 */
		if (operand1) {
			if (operand2) {
				Assert.assertTrue("At least one of the tasks should have completed", h1.hasCompleted() || h2.hasCompleted());
			} else {
				Assert.assertTrue("Task 1 should have completed", h1.hasCompleted());
				Assert.assertFalse("Task 2 should not have completed (short circuit evaluation)", h2.hasCompleted());

				Assert.assertTrue("Task 2 should have been stopped (short circuit evaluation)", h2.hasStopped());
			}
		} else {
			if (operand2) {
				Assert.assertFalse("Task 1 should not have completed (short circuit evaluation)", h1.hasCompleted());
				Assert.assertTrue("Task 2 should have completed", h2.hasCompleted());

				Assert.assertTrue("Task 1 should have been stopped (short circuit evaluation)", h1.hasStopped());
			} else {
				Assert.assertTrue("Task 1 should have completed", h1.hasCompleted());
				Assert.assertTrue("Task 2 should have completed", h2.hasCompleted());

				Assert.assertFalse("Task 1 must not have stopped", h1.hasStopped());
				Assert.assertFalse("Task 2 must not have stopped", h2.hasStopped());
			}
		}

		/*
		 * Check result
		 */
		Assert.assertEquals(disjunctionValue.get(), expectedResult);

		System.out.println();
	}

	/*
	 * Stand-in for an arbitrarily complex Boolean callable
	 */
	private boolean simulateBooleanCallable(boolean result) throws InterruptedException {
		if (!result) {
			// give other task chance to return true
			Thread.sleep(500);
		}
		return result;
	}
}
