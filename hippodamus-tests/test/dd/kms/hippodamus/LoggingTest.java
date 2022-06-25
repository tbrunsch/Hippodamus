package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;

/**
 * This test verifies that log messages are sent to the specified logger and that messages with log levels that are
 * lower than the specified minimum log level are ignored.
 */
class LoggingTest
{
	private static final int	NUM_TASKS	= 10;

	@ParameterizedTest(name = "minimum log level: {0}")
	@MethodSource("getLogLevelValues")
	void testLogger(LogLevel minLogLevel) {
		TestLogger logger = new TestLogger();
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.logger(logger)
			.minimumLogLevel(minLogLevel);
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			ExceptionalRunnable<RuntimeException> task = () -> {};
			for (int i = 0; i < NUM_TASKS; i++) {
				coordinator.execute(task);
			}
		}
		Assertions.assertEquals(0, logger.getCounter(LogLevel.INTERNAL_ERROR), "An internal error occurred");

		int expectedNumStateChangeLogMessages = minLogLevel.compareTo(LogLevel.STATE) <= 0
				? NUM_TASKS*5	// logged 5 state changes per task: SUBMITTED, EXECUTING, FINISHED, notification about result, and TERMINATED
				: 0;			// state changes not logged
		Assertions.assertEquals(expectedNumStateChangeLogMessages, logger.getCounter(LogLevel.STATE), "Wrong number of state change log messages");
	}

	static Object getLogLevelValues() {
		return LogLevel.values();
	}

	private static class TestLogger implements Logger
	{
		private final Map<LogLevel, Integer>	logLevelCounters	= new HashMap<>();

		TestLogger() {
			for (LogLevel logLevel : LogLevel.values()) {
				logLevelCounters.put(logLevel, 0);
			}
		}

		@Override
		public void log(LogLevel logLevel, String taskName, String message) {
			int counter = logLevelCounters.get(logLevel);
			logLevelCounters.put(logLevel, counter + 1);
		}

		int getCounter(LogLevel logLevel) {
			return logLevelCounters.get(logLevel);
		}
	}
}
