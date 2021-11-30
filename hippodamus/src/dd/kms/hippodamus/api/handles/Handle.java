package dd.kms.hippodamus.api.handles;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;

public interface Handle
{
	/**
	 * Submits the runnable/callable behind the handle to the underlying {@link ExecutorService}.<br/>
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
	 * @return {@code true} iff the task has been stopped manually, either manually or because of an exception<br/>
	 * <br/>
	 * If a task has completed before it had been stopped manually, then {@link #hasCompleted()} and {@link #hasStopped()}
	 * both return {@code true}.<br/>
	 * <br/>
	 * Note that stopping a task is just a request. A running task does not have to react to that request and may
	 * decide to keep running. Nevertheless, it is considered stopped.
	 */
	boolean hasStopped();

	/**
	 * Stop the task manually. This will also stop dependent tasks. If tasks, that depend on this task, will
	 * be added later to a service, then they will be assigned a handle that is already stopped and will never run.
	 */
	void stop();

	/**
	 * @return the exception, if the task associated has thrown one, or null otherwise.
	 */
	@Nullable Throwable getException();

	/**
	 * @return the name of the task associated with this handle. This is either the custom name specified
	 * 			by calling {@link ExecutionConfigurationBuilder#name(String)} before executing the task, or a
	 * 			generic name otherwise.
	 */
	String getTaskName();

	/**
	 * Waits until the task associated with this handle completes or is stopped form some reason (e.g., stopped
	 * manually, stopped due to short circuit evaluation, or stopped due to an exception).<br/>
	 * <br/>
	 * The behavior of that method depends on whether dependencies are verified or not (cf.
	 * {@link ExecutionCoordinatorBuilder#verifyDependencies(boolean)}):
	 * <ul>
	 *     <li>
	 *         If dependencies are verified, then the call throws a {@link CoordinatorException} in the
	 *         {@link ExecutionCoordinator}'s thread if the handle has not already
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
	 *         but the {@link ExecutionCoordinator}'s. In these cases, the coordinator
	 *         will send an exception, if adequate, to the coordinator's thread.
	 *     </li>
	 * </ul>
	 *
	 * @throws TaskStoppedException if the handle has been stopped
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
	 * <br/>
	 * One goal of the {@link ExecutionCoordinator} framework is to get rid of the need to install custom listeners.
	 * If you need to install a listener nevertheless, you should do it immediately after executing the task inside
	 * of the coordinator's try-block:<br/>
	 * <pre>
	 *     try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
	 *         ...
	 *         Handle handle = coordinator.execute(() -> doSomething());
	 *         handle.onCompletion(() -> onHandleCompleted(handle));
	 *         ...
	 *     }
	 * </pre>
	 * Note that if this handle is a {@link ResultHandle}, then you can access its result by calling
	 * {@link ResultHandle#get()}.
	 */
	void onCompletion(Runnable listener);

	/**
	 * Installs a listener that is called when the task throws an exception. The call will be in the thread that
	 * executed the task. If a listener is installed  after the task has thrown an exception, then the listener is
	 * called immediately in the caller's thread.<br/>
	 * <br/>
	 * One goal of the {@link ExecutionCoordinator} framework is to get rid of the need to install custom listeners.
	 * If you need to install a listener nevertheless, you should do it immediately after executing the task inside
	 * of the coordinator's try-block:<br/>
	 * <pre>
	 *     try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
	 *         ...
	 *         Handle handle = coordinator.execute(() -> doSomething());
	 *         handle.onException(() -> onExceptionCompleted(handle));
	 *         ...
	 *     }
	 * </pre>
	 * Note that you can access the exception thrown by the task associated with this handle by calling
	 * {@link #getException()}.
	 */
	void onException(Runnable listener);
}
