package dd.kms.hippodamus.samples;

import javax.annotation.Nullable;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.handles.TaskStage;

public class LoggingSample
{
	public static void main(String[] args) {
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.logger(new SampleLogger());
		try (ExecutionCoordinator coordinator = builder.build()) {
			coordinator.configure().name("'Hello' task").execute(() -> System.out.println("Hello "));
			coordinator.configure().name("'World' task").execute(() -> System.out.println("World!"));
		}
	}

	private static class SampleLogger implements Logger
	{
		@Override
		public void log(@Nullable Handle handle, String message) {
			String prefix = handle != null ? handle.getTaskName() + ": " : "";
			System.out.println(prefix + message);
		}

		@Override
		public void logStateChange(Handle handle, TaskStage taskStage) {
			log(handle, "changed to state '" + taskStage + "'");
		}

		@Override
		public void logError(@Nullable Handle handle, String error, @Nullable Throwable cause) {
			log(handle, "Error: " + error);
		}
	}
}
