package dd.kms.hippodamus.api.handles;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * When submitting a task to an {@link ExecutionCoordinator} via one of the {@code execute()}
 * methods, the submitter obtains a handle that represents that task within the coordinator.
 * This handle can be used, among others, to query information about the task. Another important
 * function is that it can be used to describe dependencies between tasks (see
 * {@link ExecutionConfigurationBuilder#dependencies(Handle...)} and
 * {@link ExecutionConfigurationBuilder#dependencies(Collection)}).
 */
public interface Handle
{
	/**
	 * @return {@code true} iff the task has already been processed
	 */
	boolean hasCompleted();

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
	 * @return The {@link ExecutionCoordinator} this handle was created by.
	 */
	ExecutionCoordinator getExecutionCoordinator();

	/**
	 * Installs a listener that is called when the task completes. That call will be in the thread that executed the
	 * task. If a listener is installed after the task has completed, then the listener is called immediately in the
	 * caller's thread.<br>
	 * <br>
	 * One goal of the {@link ExecutionCoordinator} framework is to get rid of the need to install custom listeners.
	 * If you need to install a listener nevertheless, you should do it immediately after executing the task inside
	 * of the coordinator's try-block:<br>
	 * <pre>
	 *     try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
	 *         ...
	 *         Handle handle = coordinator.execute(() -> doSomething());
	 *         handle.onCompletion(() -> onTaskCompleted(handle));
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
	 * called immediately in the caller's thread.<br>
	 * <br>
	 * One goal of the {@link ExecutionCoordinator} framework is to get rid of the need to install custom listeners.
	 * If you need to install a listener nevertheless, you should do it immediately after executing the task inside
	 * of the coordinator's try-block:<br>
	 * <pre>
	 *     try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
	 *         ...
	 *         Handle handle = coordinator.execute(() -> doSomething());
	 *         handle.onException(() -> onTaskCompletedExceptionally(handle));
	 *         ...
	 *     }
	 * </pre>
	 * Note that you can access the exception thrown by the task associated with this handle by calling
	 * {@link #getException()}.
	 */
	void onException(Runnable listener);
}
