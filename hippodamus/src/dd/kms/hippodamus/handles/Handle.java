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
	 * Waits until the task associated with this handle completes or is stopped form some reason (e.g., stopped
	 * manually, stopped due to short circuit evaluation, or stopped due to an exception).<br/>
	 * <br/>
	 * The behavior of that method depends on whether dependencies are verified or not (cf.
	 * {@link dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder#verifyDependencies(boolean)}):
	 * <ul>
	 *     <li>
	 *         If dependencies are verified, then the call throws an {@link IllegalStateException} in the
	 *         {@link dd.kms.hippodamus.coordinator.ExecutionCoordinator}'s thread if the handle has not already
	 *         completed. The reason for this is that in this mode it is assumed that tasks are never executed
	 *         before their dependencies have been resolved. If a task calls {@code #join()} of a handle,
	 *         then that handle should be listed as dependency of that task. Only if this is not the case,
	 *         which we consider an error in this mode, calling {@code join()} before the handle has completed
	 *         is possible. This justifies an exception to inform the user about a missing dependency.
	 *     </li>
	 *     <li>
	 *         If dependencies are not verified, then the call blocks until the task completes or is stopped.
	 *         In any case, the method <b>does not</b> throw an exception, but simply returns. The reason for
	 *         this is that handling exceptional behavior due to parallelism is not the tasks' responsibility,
	 *         but the {@link dd.kms.hippodamus.coordinator.ExecutionCoordinator}'s. In these cases, the coordinator
	 *         will send an exception, if adequate, to the coordinator's thread.
	 *     </li>
	 * </ul>
	 */
	void join();

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
