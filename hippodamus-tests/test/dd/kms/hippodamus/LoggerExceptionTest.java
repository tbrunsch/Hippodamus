package dd.kms.hippodamus;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.exceptions.CoordinatorException;
import dd.kms.hippodamus.logging.LogLevel;
import org.junit.Assert;
import org.junit.Test;

public class LoggerExceptionTest
{
	private static final String	EXCEPTION_TEXT	=	"Logger exception";

	@Test
	public void testExceptionInLogger() {
		ExecutionCoordinatorBuilder<?> builder = Coordinators.configureExecutionCoordinator()
			.logger(this::log);
		try (ExecutionCoordinator coordinator = builder.build()) {
			coordinator.execute(() -> {});
		} catch (CoordinatorException e){
			Assert.assertTrue("Missing logger exception text in exception", e.getMessage().contains(EXCEPTION_TEXT));
			return;
		}
		Assert.fail("Swallowed logger exception");
	}

	private void log(LogLevel logLevel, String context, String message) {
		throw new RuntimeException(EXCEPTION_TEXT);
	}
}
