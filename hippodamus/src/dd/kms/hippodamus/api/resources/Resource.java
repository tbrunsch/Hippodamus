package dd.kms.hippodamus.api.resources;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;

public interface Resource<T>
{
	/**
	 * This method is called by a task directly before it would start its execution. The resource has to decides whether
	 * the requested share can be acquired or not.
	 * <ul>
	 *     <li>
	 *         If it accepts the request, then the task will execute immediately. In this case the resource <b>must not</b>
	 *         run the {@code tryAgainRunnable}.
	 *     </li>
	 *     <li>
	 *         If it rejects the request, then it is the {@code Resource}'s responsibility to let the task repeat its
	 *         request later. This is done by running the {@code tryAgainRunnable}. The resource <b>must not</b> submit
	 *         the task again to the {@link ExecutionCoordinator} via {@link ExecutionCoordinator#execute(ExceptionalRunnable)}!
	 *     </li>
	 * </ul>
	 * @return true if the request has been accepted
	 */
	boolean tryAcquire(T resourceShare, Runnable tryAgainRunnable);

	/**
	 * This method is called when a task that has acquired a resource share terminates. The specified {@code resourceShare}
	 * is the same as the one specified when the resource has been acquired. The {@code Resource} may use this information
	 * to update internal information based on which it decides whether it accepts resource requests or not. The resource
	 * should now also decide whether some of the resource requests that have been rejected should be repeated. This is
	 * done by calling the corresponding {@code tryAgainRunnable} (see {@link #tryAcquire(Object, Runnable)}.
	 */
	void release(T resourceShare);

	/**
	 * This method is called by tasks whose {@link ExecutionCoordinator} has been stopped. If such tasks failed to acquire
	 * a share of this resource, then this resource holds a reference to the runnable that repeats the resource request.
	 * This runnable is also reference by the argument {@code tryAgainRunnable} and must now be removed from this resource.
	 * <br>
	 * <br>
	 * Note that this method might also be called when the
	 */
	void remove(Runnable tryAgainRunnable);
}
