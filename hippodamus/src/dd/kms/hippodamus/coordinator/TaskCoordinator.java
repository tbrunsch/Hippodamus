package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.concurrent.ExecutorService;

public interface TaskCoordinator extends AutoCloseable
{
	/**
	 * Executes a runnable with the default {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task. If not configured explicitly, then the default ExecutorService is the
	 * common {@link java.util.concurrent.ForkJoinPool}.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle exceptions of type E.
	 *				Note that this is the only chance for the compiler to know the exact exception type.
	 */
	<E extends Exception> Handle execute(ExceptionalRunnable<E> runnable, Handle... dependencies) throws E;

	/**
	 * Executes a runnable with the IO {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task. If not configured explicitly, then the IO ExecutorService is a dedicated
	 * single-threaded ExecutorService.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> Handle executeIO(ExceptionalRunnable<E> runnable, Handle... dependencies) throws E;

	/**
	 * Executes a runnable with the specified {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> Handle execute(ExceptionalRunnable<E> runnable, ExecutorService executorService, Handle... dependencies) throws E;

	/**
	 * Executes a callable with the default {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task. If not configured explicitly, then the default ExecutorService is the
	 * common {@link java.util.concurrent.ForkJoinPool}.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle exceptions of type E.
	 *				Note that this is the only chance for the compiler to know the exact exception type.
	 */
	<V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, Handle... dependencies) throws E;

	/**
	 * Executes a callable with the IO {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task. If not configured explicitly, then the IO ExecutorService is a dedicated
	 * single-threaded ExecutorService.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<V, E extends Exception> ResultHandle<V> executeIO(ExceptionalCallable<V, E> callable, Handle... dependencies) throws E;

	/**
	 * Executes a callable with the specified {@link ExecutorService} of this {@code TaskCoordinator} and returns
	 * a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, ExecutorService executorService, Handle... dependencies) throws E;

	/**
	 * Stops all tasks created by this service and all of their dependencies.
	 */
	void stop();

	/**
	 * Stops all dependent handles of the specified handle if the handle has been created by this service.
	 */
	void stopDependentHandles(Handle handle);

	void log(LogLevel logLevel, Handle handle, String message);

	@Override
	void close() throws InterruptedException;
}
