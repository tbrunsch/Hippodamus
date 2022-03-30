package dd.kms.hippodamus.api.execution;

import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.StoppableExceptionalRunnable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;

/**
 * This interface provides methods to execute tasks. You will usually implement against the subinterfaces
 * {@link dd.kms.hippodamus.api.coordinator.ExecutionCoordinator} and
 * {@link dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder}. You will obtain the
 * latter by calling {@link dd.kms.hippodamus.api.coordinator.ExecutionCoordinator#configure()} in case you
 * want to configure how a task will be executed.
 */
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
	 * Use this method if the task should be able to check whether it should stop prematurely.
	 * This can be the case if, e.g., another task terminates exceptionally.
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
	 * Use this method if the task should be able to check whether it should stop prematurely.
	 * This can be the case if, e.g., another task terminates exceptionally.
	 *
	 * @throws T    The exception is not really thrown here, but it forces the caller to handle T.
	 */
	<V, T extends Throwable> ResultHandle<V> execute(StoppableExceptionalCallable<V, T> callable) throws T;
}
