package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.execution.ExecutionManager;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The {@link ExecutionCoordinator} only submits tasks to the underlying {@link ExecutorService} if all of its
 * dependencies are resolved. If dependencies are not specified correctly, then tasks might have to wait for other tasks
 * to complete, blocking the thread they are executed in.<br>
 * <br>
 * Dependency verification allows to check whether such situations occur. If activated, then there will be an exception
 * when accessing the result of a task that has not yet completed. If deactivated, then the task will wait for the other
 * task to complete.<br>
 * <br>
 * This class tests several situations with activated and with deactivated dependency verification.
 */
class DependencyVerificationTest
{
	private static final int	TASK_TIME_MS	= 500;

	@ParameterizedTest(name = "verify dependencies: {0}, dependency already running: {1}")
	@MethodSource("getParameters")
	void testDependencyVerification(boolean verifyDependencies, boolean dependencyAlreadyRunning) {
		TestLogger logger = new TestLogger();
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.logger(logger)
			.verifyDependencies(verifyDependencies);
		boolean caughtException = false;
		ResultHandle<Void> dependencyTask = null;
		Handle dependentTask = null;
		TestUtils.waitForEmptyCommonForkJoinPool();
		TestEventManager eventManager = new TestEventManager();
		try (ExecutionCoordinator coordinator = TestUtils.wrap(coordinatorBuilder.build(), eventManager)) {
			Handle dummyTask = coordinator.execute(() -> TestUtils.simulateWork(TASK_TIME_MS));
			ExecutionManager executionManager = dependencyAlreadyRunning
													? coordinator
													: coordinator.configure().dependencies(dummyTask);
			dependencyTask = executionManager.execute(() -> {
				TestUtils.simulateWork(TASK_TIME_MS);
				return null;
			});

			TestUtils.simulateWork(TASK_TIME_MS / 2);
			/*
			 * At this point, dependencyTask will already be executed if and only if dependencyAlreadyRunning is true.
			 * In both cases, it will not have terminated yet.
			 */
			dependentTask = coordinator.execute(dependencyTask::get);
		} catch (CoordinatorException e) {
			caughtException = true;
		}
		Assertions.assertNotNull(dependencyTask, "dependency task has not been submitted at all");
		Assertions.assertNotNull(dependentTask, "dependent task has not been submitted at all");

		// Check test setup
		HandleEvent dependencyTaskStartedEvent = new HandleEvent(dependencyTask, HandleState.STARTED);
		HandleEvent dependencyTaskCompletedEvent = new HandleEvent(dependencyTask, HandleState.COMPLETED);
		HandleEvent dependentTaskStartedEvent = new HandleEvent(dependentTask, HandleState.STARTED);

		if (verifyDependencies) {
			if (dependencyAlreadyRunning) {
				Assertions.assertTrue(eventManager.before(dependencyTaskStartedEvent, dependentTaskStartedEvent), "dependency task should already have started when dependent task is started");
				Assertions.assertTrue(eventManager.before(dependentTaskStartedEvent, dependencyTaskCompletedEvent), "dependent task should have started before dependency task has completed");
			} else {
				Assertions.assertFalse(eventManager.encounteredEvent(dependencyTaskStartedEvent), "dependency task should not have started at all");
			}
		} else {
			Assertions.assertTrue(eventManager.before(dependentTaskStartedEvent, dependencyTaskCompletedEvent), "dependent task should have started before dependency task has completed");
			if (dependencyAlreadyRunning) {
				Assertions.assertTrue(eventManager.before(dependencyTaskStartedEvent, dependentTaskStartedEvent), "dependency task should already have started when dependent task is started");
			} else {
				Assertions.assertTrue(eventManager.before(dependentTaskStartedEvent, dependencyTaskStartedEvent), "dependency task should not have started yet when dependent task is started");
			}
		}

		// Check behavior
		if (verifyDependencies) {
			Assertions.assertTrue(logger.hasEncounteredInternalError(), "Expected an internal error due to missing dependency specification");
			Assertions.assertTrue(caughtException, "Expected an exception due to missing dependency specification");
		} else {
			Assertions.assertFalse(logger.hasEncounteredInternalError(), "Encountered an internal error");
			Assertions.assertFalse(caughtException, "Encountered an exception");
		}
	}

	static Object getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (boolean verifyDependencies : TestUtils.BOOLEANS) {
			for (boolean dependencyAlreadyRunning : TestUtils.BOOLEANS) {
				parameters.add(new Object[]{verifyDependencies, dependencyAlreadyRunning});
			}
		}
		return parameters;
	}

	private static class TestLogger implements Logger
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
