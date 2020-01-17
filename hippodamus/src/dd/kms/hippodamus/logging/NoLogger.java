package dd.kms.hippodamus.logging;

class NoLogger implements Logger
{
	@Override
	public void log(LogLevel logLevel, String taskName, String message) {
		/* ignore message */
	}
}
