package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.common.ReadableValue;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;

import java.util.concurrent.ExecutorService;

/**
 * Special {@link TaskCoordinator} for aggregating results of callables that are
 * evaluated in parallel.
 *
 * @param <S> The type of the values that are aggregated
 * @param <T> The type of the result value
 */
public interface AggregatingTaskCoordinator<S, T> extends TaskCoordinator
{
	/**
	 * Executes a callable with the default {@link ExecutorService} of this {@code AggregatingTaskCoordinator} and
	 * returns a handle to the resulting task. If not configured explicitly, then the default ExecutorService is the
	 * common {@link java.util.concurrent.ForkJoinPool}.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed. If the task
	 * completes, then its result will be aggregated.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle exceptions of type E.
	 *				Note that this is the only chance for the compiler to know the exact exception type.
	 */
	<E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable, Handle... dependencies) throws E;

	/**
	 * Executes a callable with the IO {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task. If not configured explicitly, then the IO ExecutorService is a dedicated
	 * single-threaded ExecutorService.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed. If the task
	 * completes, then its result will be aggregated.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> ResultHandle<S> aggregateIO(ExceptionalCallable<S, E> callable, Handle... dependencies) throws E;

	/**
	 * Executes a callable with the specified {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed. If the task
	 * completes, then its result will be aggregated.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable, ExecutorService executorService, Handle... dependencies) throws E;

	/**
	 * @return The {@link ReadableValue} that stores the aggregated result.
	 */
	ReadableValue<T> getResultValue();

	@Override
	void close() throws InterruptedException;
}
