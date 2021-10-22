package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.logging.LogLevel;

public class LoggingSample
{
	public static void main(String[] args) {
		ExecutionCoordinatorBuilder<?> builder = Coordinators.configureExecutionCoordinator()
			.logger((logLevel, context, message) -> System.out.println(context + ": " + message))
			.minimumLogLevel(LogLevel.STATE);
		try (ExecutionCoordinator coordinator = builder.build()) {
			coordinator.configure().name("'Hello' task").execute(() -> System.out.println("Hello "));
			coordinator.configure().name("'World' task").execute(() -> System.out.println("World!"));
		}
	}
}
