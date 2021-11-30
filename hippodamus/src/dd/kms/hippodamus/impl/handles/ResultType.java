package dd.kms.hippodamus.impl.handles;

enum ResultType
{
	/**
	 * The task has not yet been executed or it has stopped executing because it was
	 * requested to do so.
	 */
	NONE,

	/**
	 * The task has run to end without any exception. There will be a result available.
	 */
	COMPLETED,

	/**
	 * The task has terminated exceptionally. There will be an exception available.
	 */
	EXCEPTION
}
