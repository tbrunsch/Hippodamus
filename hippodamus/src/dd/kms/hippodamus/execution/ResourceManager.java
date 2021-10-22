package dd.kms.hippodamus.execution;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import dd.kms.hippodamus.resources.Resource;
import dd.kms.hippodamus.resources.ResourceShare;

import java.util.ArrayList;
import java.util.List;

class ResourceManager
{
	private final ListMultimap<Resource<?>, InternalTaskHandle>	handlesByBlockingResource			= ArrayListMultimap.create();
	private final ObjectCounter<Resource<?>>					numAcquiredResourceSharesByResource	= new ObjectCounter<>();

	/**
	 * Tries to acquire all resource shares required by the specified handle. Returns true if it succeeds.
	 * Otherwise, all already acquired resource shares are released again and the method returns false.
	 */
	boolean acquireResourceShares(InternalTaskHandle handle) {
		List<ResourceShare<?>> resourceShares = handle.getRequiredResourceShares();
		int numResourceShares = resourceShares.size();
		int numAcquiredResourceShares;
		for (numAcquiredResourceShares = 0; numAcquiredResourceShares < numResourceShares; numAcquiredResourceShares++) {
			ResourceShare<?> resourceShare = resourceShares.get(numAcquiredResourceShares);
			if (!resourceShare.acquire()) {
				break;
			}
		}
		if (numAcquiredResourceShares == numResourceShares) {
			// acquired all required resource shares
			for (ResourceShare<?> resourceShare : resourceShares) {
				Resource<?> resource = resourceShare.getResource();
				numAcquiredResourceSharesByResource.incrementCounter(resource);
			}
			return true;
		}
		// failed acquiring all required resource shares => release already acquired resource shares
		ResourceShare<?> unacquiredResourceShare = resourceShares.get(numAcquiredResourceShares);
		while (numAcquiredResourceShares > 0) {
			ResourceShare<?> resourceShare = resourceShares.get(--numAcquiredResourceShares);
			resourceShare.release();
		}
		Resource<?> blockingResource = unacquiredResourceShare.getResource();
		if (numAcquiredResourceSharesByResource.getCounter(blockingResource) == 0) {
			throw new IllegalStateException("Cannot acquire requires resource share of resource '" + blockingResource + "' required for task '" + handle + "' although no task is holding it.");
		}
		handlesByBlockingResource.put(blockingResource, handle);
		return false;
	}

	/**
	 * Releases all resource shares held by the specified handle (assuming that it holds all its
	 * required resource shares) and returns a list of {@link InternalTaskHandle}s that have
	 * been blocked by one of these resources.
	 */
	List<InternalTaskHandle> releaseResourceShares(InternalTaskHandle handle) {
		List<InternalTaskHandle> blockedHandles = null;
		List<ResourceShare<?>> resourceShares = handle.getRequiredResourceShares();
		for (ResourceShare<?> resourceShare : resourceShares) {
			Resource<?> resource = resourceShare.getResource();
			if (numAcquiredResourceSharesByResource.decrementCounter(resource) == 0) {
				List<InternalTaskHandle> blockedHandlesForResource = handlesByBlockingResource.removeAll(resource);
				if (!blockedHandlesForResource.isEmpty()) {
					if (blockedHandles == null) {
						blockedHandles = new ArrayList<>();
					}
					blockedHandles.addAll(blockedHandlesForResource);
				}
			}
			resourceShare.release();
		}
		return blockedHandles == null ? ImmutableList.of() : blockedHandles;
	}
}
