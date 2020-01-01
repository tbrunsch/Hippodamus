package dd.kms.hippodamus.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import dd.kms.hippodamus.handles.Handle;

import java.util.*;

class HandleDependencyManager
{
	/**
	 * The minimum number of elements to store in a collection for which we think
	 * that element removal is cheaper in a {@link Set} than in a {@link LinkedList}.
	 */
	private static final int	SET_THRESHOLD	= 5;

	private final TaskCoordinator					owner;
	private final Multimap<Handle, Handle>			dependentHandles			= ArrayListMultimap.create();
	private final Map<Handle, Collection<Handle>>	pendingHandleDependencies	= new HashMap<>();

	HandleDependencyManager(TaskCoordinator owner) {
		this.owner = owner;
	}

	void addDependencies(Handle handle, Handle... dependencies) {
		Preconditions.checkArgument(handle.getTaskCoordinator() == owner, "Internal error: Using dependency manager to manage handle of other task coordinator");
		Collection<Handle> handleDependencies = dependencies.length >= SET_THRESHOLD ? new HashSet<>() : new LinkedList<>();
		for (Handle dependency : dependencies) {
			if (dependency.hasCompleted()) {
				continue;
			}
			if (dependency.getTaskCoordinator() != owner) {
				// TODO: Reconsider how to handle this case
				continue;
			}
			dependentHandles.put(dependency, handle);
			handleDependencies.add(dependency);
		}
		pendingHandleDependencies.put(handle, handleDependencies);
	}

	Collection<Handle> getManagedHandles() {
		return pendingHandleDependencies.keySet();
	}

	List<Handle> getDependentHandles(Handle handle) {
		return (List<Handle>) dependentHandles.get(handle);
	}

	/**
	 * This method is called when a handle completes. It returns the handles that can now be executed.
	 */
	List<Handle> getExecutableHandles(Handle completedHandle) {
		List<Handle> dependentHandles = getDependentHandles(completedHandle);
		List<Handle> executableHandles = null;
		for (Handle dependentHandle : dependentHandles) {
			Collection<Handle> dependencies = pendingHandleDependencies.get(dependentHandle);
			dependencies.remove(completedHandle);
			if (dependencies.isEmpty()) {
				if (executableHandles == null) {
					executableHandles = new ArrayList<>();
				}
				executableHandles.add(dependentHandle);
			}
		}
		return executableHandles == null ? ImmutableList.of() : executableHandles;
	}
}
