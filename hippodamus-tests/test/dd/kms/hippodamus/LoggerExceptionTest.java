package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.logging.LogLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * This test checks that the coordinator works as expected when a logger throws an exception.
 */
class LoggerExceptionTest
{
	private static final String	EXCEPTION_TEXT	=	"Logger exception";

	@Test
	void testExceptionInLogger() {
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.logger((logLevel, taskName, message) -> {
				throw new RuntimeException(EXCEPTION_TEXT);
			})
			.minimumLogLevel(LogLevel.STATE);
		try (ExecutionCoordinator coordinator = builder.build()) {
			coordinator.execute(() -> {});
		} catch (CoordinatorException e){
			Assertions.assertTrue(e.getMessage().contains(EXCEPTION_TEXT), "Missing logger exception text in exception");
			return;
		}
		Assertions.fail("Swallowed logger exception");
	}
}
