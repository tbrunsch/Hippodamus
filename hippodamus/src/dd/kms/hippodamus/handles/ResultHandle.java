package dd.kms.hippodamus.handles;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.exceptions.CoordinatorException;

import java.util.function.Supplier;

public interface ResultHandle<T> extends Handle, Supplier<T>
{
	/**
	 * Returns the value of the callable associated with that handle.
	 *
	 * @return	The value of the callable associated with that handle.<br/>
	 * <br/>
	 * The behavior of that method depends on whether dependencies are verified or not (cf.
	 * {@link ExecutionCoordinatorBuilder#verifyDependencies(boolean)}):
	 * <ul>
	 *     <li>
	 *         If dependencies are verified, then the call returns null and throws a {@link CoordinatorException}
	 *         in the {@link ExecutionCoordinator}'s thread if the handle has not already
	 *         completed. The reason for this is that in this mode it is assumed that tasks are never executed
	 *         before their dependencies have been resolved. If a task calls {@code get()} of a result handle,
	 *         then that result handle should be listed as dependency of that task. Only if this is not the case,
	 *         which we consider an error in this mode, calling {@code get()} before the handle has completed
	 *         is possible. This justifies an exception to inform the user about a missing dependency.
	 *     </li>
	 *     <li>
	 *         If dependencies are not verified, then the call blocks until the result is available or the task
	 *         has been stopped for whatever reason (e.g., stopped manually, stopped due to short circuit evaluation,
	 *         or stopped due to an exception). In any case, the method <b>does not</b> throw an exception, but
	 *         returns the result, if available, or null otherwise. The reason for this is that handling exceptional
	 *         behavior due to parallelism is not the tasks' responsibility, but the {@link ExecutionCoordinator}'s.
	 *         In these cases, the coordinator will send an exception, if adequate, to the coordinator's thread.
	 *     </li>
	 * </ul>
	 */
	@Override
	T get();
}
