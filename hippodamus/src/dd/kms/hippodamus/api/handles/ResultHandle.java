package dd.kms.hippodamus.api.handles;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * A {@code ResultHandle} is a special {@link Handle} for tasks that return a value. It provides
 * a method {@link #get()} to access this value.
 */
public interface ResultHandle<V> extends Handle
{
	/**
	 * Returns the value of the callable associated with that handle.
	 *
	 * @return	The value of the callable associated with that handle.<br>
	 * <br>
	 * The behavior of that method depends on whether dependencies are verified or not (cf.
	 * {@link ExecutionCoordinatorBuilder#verifyDependencies(boolean)}):
	 * <ul>
	 *     <li>
	 *         If dependencies are verified, then a {@link CoordinatorException} is thrown, both in the caller's
	 *         thread and in the {@link ExecutionCoordinator}'s thread. The reason is that in this mode it is
	 *         assumed that tasks are never executed before their dependencies have been resolved. If a task calls
	 *         {@code get()} of a result handle, then this result handle should be listed as dependency of
	 *         that task.
	 *     </li>
	 *     <li>
	 *         If dependencies are not verified, then the call blocks until the result is available or the task
	 *         has terminated exceptionally.
	 *     </li>
	 * </ul>
	 *
	 * @throws CoordinatorException if dependency verification is activated and the task has not yet terminated
	 * @throws CompletionException if the task has terminated exceptionally.
	 * @throws CancellationException if the task has been stopped and has not been executed until then
	 */
	V get() throws CoordinatorException, CompletionException, CancellationException;
}
