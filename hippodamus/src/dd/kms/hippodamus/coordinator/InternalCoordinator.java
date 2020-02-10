package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.concurrent.Semaphore;

public interface InternalCoordinator extends ExecutionCoordinator
{
	/**
	 * Stops all dependent handles of the specified handle if the handle has been created by this service.
	 */
	void stopDependentHandles(Handle handle);

	void onCompletion(Handle handle);

	void onException(Handle handle);

	/**
	 * Logs a message for a certain handle at a certain log message.<br/>
	 * <br/>
	 * Ensure that this method is only called with locking the coordinator.
	 */
	void log(LogLevel logLevel, Handle handle, String message);

	/**
	 * @return The coordinators termination lock. This lock is hold by all handles managed by the coordinator.
	 * The coordinator will wait in its {@link #close()} method until all tasks have released it.<br/>
	 * <br/>
	 * Handles must release it when terminating, either successfully or exceptionally, or when being stopped.
	 */
	Semaphore getTerminationLock();
}
