package dd.kms.hippodamus.api.execution;

import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.api.handles.ResultHandle;

public interface AggregationManager<S> extends ExecutionManager
{
	/**
	 * Executes an {@link ExceptionalCallable} and returns a handle to the resulting task.<br>
	 * <br>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed. If the
	 * task completes, then its result will be aggregated.
	 *
	 * @throws T    The exception is not really thrown here, but it forces the caller to handle T.
	 */
	<T extends Throwable> ResultHandle<S> aggregate(ExceptionalCallable<S, T> callable) throws T;

	/**
	 * Executes a {@link StoppableExceptionalCallable} and returns a handle to the resulting task.<br>
	 * <br>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed. If the
	 * task completes, then its result will be aggregated.
	 *
	 * @throws T    The exception is not really thrown here, but it forces the caller to handle T.
	 */
	<T extends Throwable> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, T> callable) throws T;
}
