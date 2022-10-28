package dd.kms.hippodamus;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.TaskStage;
import dd.kms.hippodamus.api.logging.Logger;

/**
 * This test checks that the coordinator works as expected when a logger throws an exception.
 */
class LoggerExceptionTest
{
	private static final String	EXCEPTION_TEXT	=	"Logger exception";

	@Test
	void testExceptionInLogger() {
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.logger(new ExceptionalLogger());
		try (ExecutionCoordinator coordinator = builder.build()) {
			coordinator.execute(() -> {});
		} catch (CoordinatorException e){
			Assertions.assertTrue(e.getMessage().contains(EXCEPTION_TEXT), "Missing logger exception text in exception");
			return;
		}
		Assertions.fail("Swallowed logger exception");
	}

	private static class ExceptionalLogger implements Logger
	{
		@Override
		public void log(@Nullable Handle handle, String message) {
			/* do nothing */
		}

		@Override
		public void logStateChange(Handle handle, TaskStage taskStage) {
			throw new RuntimeException(EXCEPTION_TEXT);
		}

		@Override
		public void logError(@Nullable Handle handle, String error, @Nullable Throwable cause) {
			/* do nothing */
		}
	}
}
