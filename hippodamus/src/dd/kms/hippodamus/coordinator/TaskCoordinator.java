package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalRunnable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.concurrent.ExecutorService;

public interface TaskCoordinator extends AutoCloseable
{
	/**
	 * Executes a runnable with the {@link ExecutorService} specified by {@code executorServiceId} and returns
	 * a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed.<br/>
	 * <br/>
	 * Use the predefined executor service IDs {@link ExecutorServiceIds#REGULAR} and {@link ExecutorServiceIds#IO}
	 * to refer to the executor service for regular and IO tasks, respectively. You can also use a custom ID to
	 * refer to an executor service you have registered under for that ID before.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> Handle execute(ExceptionalRunnable<E> runnable, int executorServiceId, Handle... dependencies) throws E;

	/**
	 * Executes a stoppable runnable with the {@link ExecutorService} specified by {@code executorServiceId} and
	 * returns a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the {@code ExecutorService} unless all dependencies have completed.<br/>
	 * <br/>
	 * Use the predefined executor service IDs {@link ExecutorServiceIds#REGULAR} and {@link ExecutorServiceIds#IO}
	 * to refer to the executor service for regular and IO tasks, respectively. You can also use a custom ID to
	 * refer to an executor service you have registered under for that ID before.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> Handle execute(StoppableExceptionalRunnable<E> runnable, int executorServiceId, Handle... dependencies) throws E;

	/**
	 * Executes a callable with the specified {@link ExecutorService} specified by {@code executorServiceId} and
	 * returns a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.<br/>
	 * <br/>
	 * Use the predefined executor service IDs {@link ExecutorServiceIds#REGULAR} and {@link ExecutorServiceIds#IO}
	 * to refer to the executor service for regular and IO tasks, respectively. You can also use a custom ID to
	 * refer to an executor service you have registered under for that ID before.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, int executorServiceId, Handle... dependencies) throws E;

	/**
	 * Executes a stoppable callable with the specified {@link ExecutorService} specified by {@code executorServiceId}
	 * and returns a handle to the resulting task.<br/>
	 * <br/>
	 * The task will not be submitted to the ExecutorService unless all dependencies have completed.<br/>
	 * <br/>
	 * Use the predefined executor service IDs {@link ExecutorServiceIds#REGULAR} and {@link ExecutorServiceIds#IO}
	 * to refer to the executor service for regular and IO tasks, respectively. You can also use a custom ID to
	 * refer to an executor service you have registered under for that ID before.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable, int executorServiceId, Handle... dependencies) throws E;

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
