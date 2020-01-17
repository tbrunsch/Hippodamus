package dd.kms.hippodamus.execution;

import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalRunnable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;

public interface ExecutionManager
{
	/**
	 * Executes an {@link ExceptionalRunnable} and returns a handle to the resulting task.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> Handle execute(ExceptionalRunnable<E> runnable) throws E;

	/**
	 * Executes a {@link StoppableExceptionalRunnable} and returns a handle to the resulting task.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<E extends Exception> Handle execute(StoppableExceptionalRunnable<E> runnable) throws E;

	/**
	 * Executes an {@link ExceptionalCallable} and returns a handle to the resulting task.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable) throws E;

	/**
	 * Executes a {@link StoppableExceptionalCallable} and returns a handle to the resulting task.
	 *
	 * @throws E	The exception is not really thrown here, but it forces the caller to handle E.
	 */
	<V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable) throws E;
}
