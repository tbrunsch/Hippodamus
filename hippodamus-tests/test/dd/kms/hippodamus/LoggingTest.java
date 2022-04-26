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

import java.util.HashSet;
import java.util.Set;

/**
 * This test verifies that log messages are send to the specified logger
 * and that messages with a too low log level are ignored.
 */
class LoggingTest
{
	static Object getParameters() {
		return LogLevel.values();
	}

	@ParameterizedTest(name = "exception in tasks: {0}, {1}")
	@MethodSource("getParameters")
	void testLogger(LogLevel minLogLevel) {
		TestLogger logger = new TestLogger();
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.logger(logger)
			.minimumLogLevel(minLogLevel);
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			ExceptionalRunnable<RuntimeException> task = () -> {};
			for (int i = 0; i < 10; i++) {
				coordinator.execute(task);
			}
		}
		Set<LogLevel> logLevels = logger.getEncounteredLogLevels();
		for (LogLevel logLevel : LogLevel.values()) {
			boolean logLevelEncountered = logLevels.contains(logLevel);
			if (logLevel == LogLevel.INTERNAL_ERROR) {
				Assertions.assertFalse(logLevelEncountered, "Encountered an internal error");
				continue;
			}
			boolean logLevelExpected = logLevel.compareTo(minLogLevel) <= 0;
			if (logLevelExpected) {
				Assertions.assertTrue(logLevelEncountered, "Log message of level '" + logLevel + "' has been swallowed");
			} else {
				Assertions.assertFalse(logLevelEncountered, "Encountered a log message of level '" + logLevel + "' which should have been ignored");
			}
		}
	}

	private static class TestLogger implements Logger
	{
		private final Set<LogLevel>	encounteredLogLevels	= new HashSet<>();

		@Override
		public void log(LogLevel logLevel, String taskName, String message) {
			encounteredLogLevels.add(logLevel);
		}

		Set<LogLevel> getEncounteredLogLevels() {
			return encounteredLogLevels;
		}
	}
}
