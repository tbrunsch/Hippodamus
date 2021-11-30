package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.logging.LogLevel;

public class LoggingSample
{
	public static void main(String[] args) {
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.logger((logLevel, taskName, message) -> System.out.println(taskName + ": " + message))
			.minimumLogLevel(LogLevel.STATE);
		try (ExecutionCoordinator coordinator = builder.build()) {
			coordinator.configure().name("'Hello' task").execute(() -> System.out.println("Hello "));
			coordinator.configure().name("'World' task").execute(() -> System.out.println("World!"));
		}
	}
}
