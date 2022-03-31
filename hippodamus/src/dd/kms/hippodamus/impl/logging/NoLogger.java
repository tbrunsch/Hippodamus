package dd.kms.hippodamus.impl.logging;

import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;

public class NoLogger implements Logger
{
	public static final Logger	LOGGER	= new NoLogger();

	@Override
	public void log(LogLevel logLevel, String taskName, String message) {
		/* ignore message */
	}
}
