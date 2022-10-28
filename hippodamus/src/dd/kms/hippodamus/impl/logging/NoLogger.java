package dd.kms.hippodamus.impl.logging;

import javax.annotation.Nullable;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.TaskStage;
import dd.kms.hippodamus.api.logging.Logger;

public class NoLogger implements Logger
{
	public static final Logger	LOGGER	= new NoLogger();

	@Override
	public void log(@Nullable Handle handle, String message) {
		/* do nothing */
	}

	@Override
	public void logStateChange(Handle handle, TaskStage taskStage) {
		/* do nothing */
	}

	@Override
	public void logError(@Nullable Handle handle, String error, @Nullable Throwable cause) {
		/* do nothing */
	}
}
