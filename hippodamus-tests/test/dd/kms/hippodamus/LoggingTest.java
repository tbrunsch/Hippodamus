package dd.kms.hippodamus;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.handles.TaskStage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

/**
 * This test verifies that log messages are sent to the specified logger and that messages with log levels that are
 * lower than the specified minimum log level are ignored.
 */
class LoggingTest
{
	private static final int	NUM_TASKS	= 10;

	@Test
	void testLogger() {
		TestLogger logger = new TestLogger();
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.logger(logger);
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			ExceptionalRunnable<RuntimeException> task = () -> {};
			for (int i = 0; i < NUM_TASKS; i++) {
				coordinator.execute(task);
			}
		}
		Assertions.assertEquals(0, logger.getNumberOfErrors(), "An internal error occurred");

		Set<TaskStage> expectedTaskStages = ImmutableSet.of(TaskStage.SUBMITTED, TaskStage.EXECUTING, TaskStage.FINISHED, TaskStage.TERMINATED);
		for (TaskStage taskStage : TaskStage.values()) {
			boolean stageExpected = expectedTaskStages.contains(taskStage);
			int expectedNumOccurrences = stageExpected ? NUM_TASKS : 0;
			Assertions.assertEquals(expectedNumOccurrences, logger.getNumberOfChangesTo(taskStage), "Wrong number of state change log messages");
		}
	}

	private static class TestLogger implements Logger
	{
		private final Map<TaskStage, Integer>	taskStageCounters	= new HashMap<>();
		private int 							numErrors			= 0;

		TestLogger() {
			for (TaskStage taskStage : TaskStage.values()) {
				taskStageCounters.put(taskStage, 0);
			}
		}

		public void log(@Nullable Handle handle, String message) {
			/* do nothing */
		}

		@Override
		public void logStateChange(Handle handle, TaskStage taskStage) {
			int counter = taskStageCounters.get(taskStage);
			taskStageCounters.put(taskStage, counter + 1);
		}

		@Override
		public void logError(@Nullable Handle handle, String error, @Nullable Throwable cause) {
			numErrors++;
		}

		int getNumberOfChangesTo(TaskStage taskStage) {
			return taskStageCounters.get(taskStage);
		}

		int getNumberOfErrors() {
			return numErrors;
		}
	}
}
