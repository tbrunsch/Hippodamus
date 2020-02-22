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
	 * @throws T    The exception is not really thrown here, but it forces the caller to handle T.
	 */
	<T extends Throwable> Handle execute(ExceptionalRunnable<T> runnable) throws T;

	/**
	 * Executes a {@link StoppableExceptionalRunnable} and returns a handle to the resulting task.
	 *
	 * @throws T    The exception is not really thrown here, but it forces the caller to handle T.
	 */
	<T extends Throwable> Handle execute(StoppableExceptionalRunnable<T> runnable) throws T;

	/**
	 * Executes an {@link ExceptionalCallable} and returns a handle to the resulting task.
	 *
	 * @throws T    The exception is not really thrown here, but it forces the caller to handle T.
	 */
	<V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) throws T;

	/**
	 * Executes a {@link StoppableExceptionalCallable} and returns a handle to the resulting task.
	 *
	 * @throws T    The exception is not really thrown here, but it forces the caller to handle T.
	 */
	<V, T extends Throwable> ResultHandle<V> execute(StoppableExceptionalCallable<V, T> callable) throws T;
}
