package dd.kms.hippodamus.handles;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;

import java.util.function.Consumer;

public interface Handle
{
	/**
	 * Submits the runnable/callable behind the handle to the underlying {@link java.util.concurrent.ExecutorService}.<br/>
	 * <br/>
	 * Users should not call this method directly. It will be called by the framework if all of the handle's dependencies
	 * have completed.
	 */
	void submit();

	/**
	 * @return {@code true} iff the task has already been processed
	 */
	boolean hasCompleted();

	/**
	 * @return {@code true} iff the task has been stopped manually, either manually or because of an exception
	 *
	 * If a task has completed before it had been stopped manually, then {@link #hasCompleted()} and {@link #hasStopped()}
	 * both return {@code true}.
	 */
	boolean hasStopped();

	/**
	 * Stop the task manually. This will also stop dependent tasks. If tasks, that depend on this task, will
	 * be added later to a service, then they will be assigned a handle that is already stopped and will never run.
	 */
	void stop();

	/**
	 * @return The {@link ExecutionCoordinator} this handle was created by.
	 */
	ExecutionCoordinator getExecutionCoordinator();

	/**
	 * Installs a listener that is called when the task completes. That call will be in the thread that executed the
	 * task. If a listener is installed after the task has completed, then the listener is called immediately in the
	 * caller's thread.<br/>
	 */
	void onCompletion(Runnable listener);

	/**
	 * Installs an exception handler that is called if the tasks raises an exception. The call will be in the thread that
	 * executed the task. If a handler is installed  after the task has raised an exception, then the handler is called
	 * immediately in the caller's thread.<br/>
	 */
	void onException(Consumer<Throwable> exceptionHandler);
}
