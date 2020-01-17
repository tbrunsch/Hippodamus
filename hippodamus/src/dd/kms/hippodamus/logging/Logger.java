package dd.kms.hippodamus.logging;

public interface Logger
{
	void log(LogLevel logLevel, String taskName, String message);
}
