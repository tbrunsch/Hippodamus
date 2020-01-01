package dd.kms.hippodamus.logging;

public enum LogLevel
{
	/**
	 * Messages on this level describe internal errors of the parallelization framework.
	 */
	INTERNAL_ERROR,

	/**
	 * Messages on this level describe state changes of {@link dd.kms.hippodamus.handles.Handle}s.
	 */
	STATE,

	/**
	 * Messages on this level are meant for debugging.
	 */
	DEBUGGING
}
