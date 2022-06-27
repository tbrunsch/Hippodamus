package dd.kms.hippodamus.impl.coordinator;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import dd.kms.hippodamus.api.handles.Handle;

import java.util.*;

class HandleDependencyManager
{
	/**
	 * The minimum number of elements to store in a collection for which we think
	 * that element removal is cheaper in a {@link Set} than in a {@link LinkedList}.
	 */
	private static final int	SET_THRESHOLD	= 5;

	private final Multimap<Handle, Handle>			dependentHandles			= ArrayListMultimap.create();
	private final Map<Handle, Collection<Handle>>	pendingHandleDependencies	= new HashMap<>();

	void addDependencies(Handle handle, Collection<Handle> dependencies) {
		Collection<Handle> handleDependencies = dependencies.size() >= SET_THRESHOLD ? new HashSet<>() : new LinkedList<>();
		for (Handle dependency : dependencies) {
			if (dependency.hasCompleted()) {
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

	int getNumberOfManagedHandles() {
		return getManagedHandles().size();
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
