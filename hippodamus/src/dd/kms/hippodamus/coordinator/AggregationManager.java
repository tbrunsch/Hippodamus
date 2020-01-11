package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.handles.ResultHandle;

interface AggregationManager<S>
{
	/**
	 * Executes an {@link ExceptionalCallable} and returns a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed. If the
	 * task completes, then its result will be aggregated.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable) throws E;

	/**
	 * Executes a {@link StoppableExceptionalCallable} and returns a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed. If the
	 * task completes, then its result will be aggregated.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable) throws E;
}
