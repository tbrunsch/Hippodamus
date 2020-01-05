package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

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
	 * Executes a callable with the the {@link ExecutorService} specified by {@code executorServiceId} and returns
	 * a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed. If the
	 * task completes, then its result will be aggregated.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable, int executorServiceId, Handle... dependencies) throws E;

	/**
	 * Executes a stoppable callable with the the {@link ExecutorService} specified by {@code executorServiceId} and returns
	 * a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed. If the
	 * task completes, then its result will be aggregated.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable, int executorServiceId, Handle... dependencies) throws E;

	/**
	 * @return The {@link Supplier} that stores the aggregated result.
	 */
	Supplier<T> getResultValue();

	@Override
	void close() throws InterruptedException;
}
