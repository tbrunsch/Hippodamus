package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.HashSet;
import java.util.Set;

/**
 * This test verifies that log messages are send to the specified logger
 * and that messages with a too low log level are ignored.
 */
@RunWith(Parameterized.class)
public class LoggingTest
{
	@Parameterized.Parameters(name = "exception in tasks: {0}, {1}")
	public static Object getParameters() {
		return LogLevel.values();
	}

	private final LogLevel	minLogLevel;

	public LoggingTest(LogLevel minLogLevel) {
		this.minLogLevel = minLogLevel;
	}

	@Test
	public void testLogger() {
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
				Assert.assertFalse("Encountered an internal error", logLevelEncountered);
				continue;
			}
			boolean logLevelExpected = logLevel.compareTo(minLogLevel) <= 0;
			if (logLevelExpected) {
				Assert.assertTrue("Log message of level '" + logLevel + "' has been swallowed", logLevelEncountered);
			} else {
				Assert.assertFalse("Encountered a log message of level '" + logLevel + "' which should have been ignored", logLevelEncountered);
			}
		}
	}

	private class TestLogger implements Logger
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
