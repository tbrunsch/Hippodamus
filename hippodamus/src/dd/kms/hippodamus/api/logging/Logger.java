package dd.kms.hippodamus.api.logging;

import javax.annotation.Nullable;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.TaskStage;

/**
 * Implement your own {@code Logger} class and register an instance of it via
 * {@link dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder#logger(Logger)}
 * to get informed about some internals of Hippodamus like state changes and internal errors.
 */
public interface Logger
{
	void log(@Nullable Handle handle, String message);

	void logStateChange(Handle handle, TaskStage taskStage);

	void logError(@Nullable Handle handle, String error, @Nullable Throwable cause);
}
