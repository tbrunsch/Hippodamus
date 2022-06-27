package dd.kms.hippodamus.api.logging;

/**
 * Implement your own {@code Logger} class and register an instance of it via
 * {@link dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder#logger(Logger)}
 * to get informed about some internals of Hippodamus. These internals are state changes
 * and internal errors.
 */
public interface Logger
{
	/**
	 * If every logger instance is only used within one {@link dd.kms.hippodamus.api.coordinator.ExecutionCoordinator},
	 * then implementers do not have to care about synchronization. The coordinator ensures that this method
	 * is not called concurrently from its tasks. However, if a logger instance might be used concurrently for
	 * multiple coordinators, then you have to synchronize the calls of {@code log()}.
	 */
	void log(LogLevel logLevel, String taskName, String message);
}
