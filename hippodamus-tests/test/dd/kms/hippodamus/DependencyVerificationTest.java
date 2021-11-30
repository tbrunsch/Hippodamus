package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.concurrent.ExecutorService;

/**
 * The {@link ExecutionCoordinator} only
 * submits tasks to the {@link ExecutorService}, if
 * all of its dependencies are resolved. If dependencies are not specified
 * correctly, then tasks might end up actively waiting for other tasks to
 * complete.<br/>
 * <br/>
 * Dependency verification allows to check whether for all tasks all
 * dependencies are specified. If activated, then there will be an exception
 * when accessing the result of a task that has not yet completed. If
 * deactivated, then the task will wait for the other task to complete.<br/>
 * <br/>
 * This task focuses on testing both modi, which should be both supported
 * by the framework.
 */
@RunWith(Parameterized.class)
public class DependencyVerificationTest
{
	@Parameterized.Parameters(name = "verify dependencies: {0}")
	public static Object getParameters() {
		return new Object[]{ false, true };
	}

	private final boolean	verifyDependencies;

	public DependencyVerificationTest(boolean verifyDependencies) {
		this.verifyDependencies = verifyDependencies;
	}

	@Test
	public void testNonSubmittedTaskDependency() {
		TestLogger logger = new TestLogger();
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.logger(logger)
			.verifyDependencies(verifyDependencies);
		ResultHandle<Integer> task3 = null;
		boolean caughtException = false;
		int task1Result = 13;
		int task2Result = 17;
		TestUtils.waitForEmptyCommonForkJoinPool();
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			ResultHandle<Integer> task1 = coordinator.execute(() -> generateNumber(task1Result, 1000));
			ResultHandle<Integer> task2 = coordinator.configure().dependencies(task1).execute(() -> generateNumber(task2Result, 100));

			/*
			 * Task 3 depend on the result of task 2, but that dependency is not specified.
			 * If task 3 is executed, task 2 will not even be submitted to an ExecutorService
			 * because it is waiting in task 1.
			 */
			task3 = coordinator.execute(() -> task2.get());
		} catch (CoordinatorException e) {
			caughtException = true;
		}
		Assert.assertTrue("task3 is null", task3 != null);
		if (verifyDependencies) {
			Assert.assertTrue("Expected an internal error due to missing dependency specification", logger.hasEncounteredInternalError());
			Assert.assertTrue("Expected an exception due to missing dependency specification", caughtException);
		} else {
			Assert.assertFalse("Encountered an internal error", logger.hasEncounteredInternalError());
			Assert.assertFalse("Encountered an exception", caughtException);
			Assert.assertEquals(task2Result, (int) task3.get());
		}
	}

	@Test
	public void testRunningTaskDependency() {
		TestLogger logger = new TestLogger();
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.logger(logger)
			.verifyDependencies(verifyDependencies);
		boolean caughtException = false;
		int addend1Result = 13;
		int addend2Result = 17;
		ResultHandle<Integer> sum = null;
		TestUtils.waitForEmptyCommonForkJoinPool();
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			ResultHandle<Integer> addend1 = coordinator.execute(() -> generateNumber(addend1Result, 1000));
			ResultHandle<Integer> addend2 = coordinator.execute(() -> generateNumber(addend2Result, 500));
			sum = coordinator.execute(() -> addend1.get() + addend2.get());
		} catch (CoordinatorException e) {
			caughtException = true;
		}
		Assert.assertTrue("sum is null", sum != null);
		if (verifyDependencies) {
			Assert.assertTrue("Expected an internal error due to missing dependency specification", logger.hasEncounteredInternalError());
			Assert.assertTrue("Expected an exception due to missing dependency specification", caughtException);
		} else {
			Assert.assertFalse("Encountered an internal error", logger.hasEncounteredInternalError());
			Assert.assertFalse("Encountered an exception", caughtException);
			Assert.assertEquals(addend1Result + addend2Result, (int) sum.get());
		}
	}

	private int generateNumber(int number, long simulatedTimeMs) {
		TestUtils.simulateWork(simulatedTimeMs);
		return number;
	}

	private class TestLogger implements Logger
	{
		private boolean encounteredInternalError;

		@Override
		public void log(LogLevel logLevel, String taskName, String message) {
			encounteredInternalError |= (logLevel == LogLevel.INTERNAL_ERROR);
		}

		boolean hasEncounteredInternalError() {
			return encounteredInternalError;
		}
	}
}
