package dd.kms.hippodamus.api.resources;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;

/**
 * This interface describes a resource. The generic parameter {@code T} describes the type of the shares you can
 * acquire from it. For instance, if the resource something countable, then {@code T = Long}. If the resource is a file
 * system, then {@code T} could represent a file.<br>
 * <br>
 * Resources have to decide whether to accept or reject requests for a certain resource share (see {@link #tryAcquire(Object, Runnable)}.
 * If a resource rejects a request, it is the resource's responsibility to resubmit the task later.<br>
 * <br>
 * Resource implementations should distinguish between
 * <ol>
 *     <li>resource shares that have already been acquired and </li>
 *     <li>resource shares of tasks that have been submitted but not yet started.</li>
 * </ol>
 * The first aspect is important to decide whether a resource request can be accepted or has to be rejected. The second
 * aspect is additionally important to decide which and how many postponed tasks to resubmit, e.g., when parts of the
 * resource become available again.<br>
 * <br>
 * @implSpec Hippodamus will always inform a resource about any change in one of these types of resource shares:
 * <ol>
 *     <li>
 *         Before a task starts its execution, Hippodamus calls {@link #tryAcquire(Object, Runnable)}. If this method
 *         accepts the request, then it has to update its information about the acquired resource shares. When a task
 *         finishes, Hippodamus calls {@link #release(Object)}, in which case the resource also has to update this
 *         information.
 *     </li>
 *     <li>
 *         When a task is submitted, Hippodamus calls {@link #addPendingResourceShare(Object)} to inform the resource
 *         about an upcoming resource request. When a task changes from "submitted" to any other state, then Hippodamus
 *         calls {@link #removePendingResourceShare(Object)}. Information about submitted tasks must only be updated
 *         within these two methods.
 *     </li>
 * </ol>
 * It is also important to implement a {@code Resource} thread-safe.
 */
public interface Resource<T>
{
	/**
	 * This method is called when a task is submitted that requires a share of this resource. It has nothing to do with
	 * an attempt to acquire that share. The sole purpose of this method is to enrich the {@code Resource}'s information
	 * about what is already submitted. The {@code Resource} can leverage this information when deciding which of the
	 * postponed tasks to resubmit and how many.
	 */
	void addPendingResourceShare(T resourceShare);

	/**
	 * This method is called when a task changes its state from "submitted" to something else. The {@code resourceShare}
	 * is the share it would try to acquire when executing. The {@code Resource} can use this information to update
	 * internal information based on which it decides which and how many of the postponed tasks to resubmit. When this
	 * method is called it might be a good time to resubmit postponed tasks if enough of the resource is available.<br>
	 * <br>
	 * Note that Hippodamus automatically calls this method, among others, after a resource request, independent of
	 * whether it has been accepted or not. Resources <b>must not update</b> information about pending resource shares
	 * their own, e.g., in {@link #tryAcquire(Object, Runnable)}.
	 */
	void removePendingResourceShare(T resourceShare);

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
	 * This runnable is also referenced by the argument {@code tryAgainRunnable} and must now be removed from this resource.
	 * Note, however, that this method might also be called when this resource does not hold a reference to this
	 * {@code Runnable}.
	 */
	void remove(Runnable tryAgainRunnable);
}
