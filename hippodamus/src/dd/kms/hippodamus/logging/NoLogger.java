package dd.kms.hippodamus.logging;

class NoLogger implements Logger
{
	@Override
	public void log(LogLevel logLevel, String context, String message) {
		/* ignore message */
	}
}
