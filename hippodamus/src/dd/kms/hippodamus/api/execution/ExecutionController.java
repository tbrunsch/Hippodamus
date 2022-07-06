package dd.kms.hippodamus.api.execution;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;

/**
 * A user-defined {@code ExecutionController} can be added to every task via
 * {@link ExecutionConfigurationBuilder#executionController(ExecutionController)} in order to postpone the execution of
 * a task in certain situations. A typical situation is that a task requires much memory and there is currently a high
 * memory consumption. These situations cannot be describes via task dependencies and can usually not be controlled fine
 * enough by specifying the maximum parallelism.<br>
 * <br>
 * Since it is highly user-specific how these situations should be handled, there is no default implementation for this
 * interface you can use. However, there are some sample implementations in some unit tests that are tailored to these
 * tests. These can give an idea of how to implement this interface.<br>
 * <br>
 * Usually you create a dedicated instance of this interface for each task. Hence, each instance implicitly knows which
 * task it belongs to.
 */
public interface ExecutionController
{
	/**
	 * This method is called by a task directly before it would start its execution. This method then decides whether
	 * the task may execute now or not.
	 * <ul>
	 *     <li>
	 *         If it permits the task to execute, then the task will execute immediately. In this case you <b>must
	 *         not</b> run the {@code submitLaterRunnable}.
	 *     </li>
	 *     <li>
	 *         If it does not permit the task to execute, then it is the {@link ExecutionController}'s responsibility to
	 *         submit the task again later. This must be done by running the {@code submitLaterRunnable}. You <b>must
	 *         not</b> submit the task again to the {@link ExecutionCoordinator} via {@link ExecutionCoordinator#execute(ExceptionalRunnable)}!
	 *     </li>
	 * </ul>
	 *
	 * @param submitLaterRunnable
	 * @return
	 */
	boolean permitExecution(Runnable submitLaterRunnable);

	/**
	 * This method is called when the {@link ExecutionCoordinator} tries to close prematurely. After this method has
	 * been called this {@link ExecutionController} must not submit the task again. If the {@code ExecutionController}
	 * has already allowed the task to execute, then nothing has to be done.
	 */
	void stop();

	/**
	 * Informs this {@link ExecutionController} when the task finishes, either successfully or exceptionally. The
	 * {@code ExecutionController} may use this information to update internal information based on which it decides
	 * whether tasks may be executed or not and whether tasks that have been put on hold may be submitted now.
	 */
	void finishedExecution(boolean finishedSuccessfully);
}
