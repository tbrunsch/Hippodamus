package dd.kms.hippodamus.resources.memory;

/**
 * This class is an attempt to model the available memory as a resource. Whenever it has to be decided whether a task
 * can be executed, the available amount of memory is considered. Unlike an optimistic approach, which would simply
 * consider the currently available memory, it uses a pessimistic approach: It takes the tasks that are currently
 * executed into consideration by subtracting their pretended sizes from the currently available memory. So while the
 * optimistic approach would assume that all tasks that are currently executed have already acquired all the memory
 * they need, this approach assumes that all these tasks have not acquired any memory at all yet. Both approaches are
 * not realistic, but it is almost impossible to determine how much memory each task will acquire in the future. The
 * pessimistic approach is a bit safer with respect to avoiding {@link OutOfMemoryError}s.<br>
 * <br>
 * Note that this implementation has several problems when memory is acquired in parallel and not all memory allocations
 * are tracked by this resource for whatever reason:
 * <ul>
 *     <li>
 *         Even this pessimistic approach could overestimate the available memory because some thread could suddenly
 *         consume a lot of memory the resource could not take into consideration. This could lead to an
 *         {@code OutOfMemoryError}.
 *     </li>
 *     <li>
 *			Compared to {@link DefaultCountableResource}, postponed tasks could suffer from starvation: These tasks
 *		    will only be resubmitted when another task finishes <b>and</b> there is enough available memory for such a
 *		    task. When the last submitted task terminates and yet there is not enough memory available for at least one
 *		    of the postponed tasks, then these task have no change to get resubmitted anymore.
 *     </li>
 * </ul>
 * Additionally, this implementation inherits the problems of {@link AbstractCountableResource}.
 */
class MemoryResource extends AbstractCountableResource
{
	static final CountableResource	RESOURCE	= new MemoryResource();

	private MemoryResource() {
		super("Memory");
	}

	@Override
	long getCapacity() {
		return MemoryUtils.getAvailableMemory();
	}
}
