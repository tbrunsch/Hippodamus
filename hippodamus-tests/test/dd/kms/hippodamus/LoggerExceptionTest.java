package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.logging.LogLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LoggerExceptionTest
{
	private static final String	EXCEPTION_TEXT	=	"Logger exception";

	@Test
	void testExceptionInLogger() {
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.logger(this::log);
		try (ExecutionCoordinator coordinator = builder.build()) {
			coordinator.execute(() -> {});
		} catch (CoordinatorException e){
			Assertions.assertTrue(e.getMessage().contains(EXCEPTION_TEXT), "Missing logger exception text in exception");
			return;
		}
		Assertions.fail("Swallowed logger exception");
	}

	private void log(LogLevel logLevel, String taskName, String message) {
		throw new RuntimeException(EXCEPTION_TEXT);
	}
}
