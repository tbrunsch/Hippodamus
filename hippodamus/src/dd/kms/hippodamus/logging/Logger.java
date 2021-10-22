package dd.kms.hippodamus.logging;

public interface Logger
{
	/**
	 * Implementers do not have to care about synchronization. The framework
	 * ensures that this method is not called concurrently.
	 */
	void log(LogLevel logLevel, String context, String message);
}
