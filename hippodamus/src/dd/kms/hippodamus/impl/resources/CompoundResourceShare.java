package dd.kms.hippodamus.impl.resources;

import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.api.resources.ResourceRequestor;

import java.util.List;

/**
 * This class combines multiple resource shares into one such that {@link dd.kms.hippodamus.impl.handles.HandleImpl}
 * only has to deal with a single resource share.
 */
class CompoundResourceShare implements ResourceShare
{
	private final List<ResourceShare>	resourcesShares;
	private int							rejectedResourceIndex	= -1;

	public CompoundResourceShare(List<? extends ResourceShare> resourcesShares) {
		this.resourcesShares = ImmutableList.copyOf(resourcesShares);
	}

	@Override
	public void addPendingResourceShare() {
		for (ResourceShare resourcesShare : resourcesShares) {
			resourcesShare.addPendingResourceShare();
		}
	}

	@Override
	public void removePendingResourceShare() {
		for (ResourceShare resourcesShare : resourcesShares) {
			resourcesShare.removePendingResourceShare();
		}
	}

	@Override
	public boolean tryAcquire(ResourceRequestor resourceRequestor) {
		int rejectedResourceIndex = acquireResources(resourceRequestor);
		if (rejectedResourceIndex == -1) {
			return true;
		}
		releaseResources(rejectedResourceIndex);
		return false;
	}

	private int acquireResources(ResourceRequestor resourceRequestor) {
		int numResources = resourcesShares.size();
		for (int i = 0; i < numResources; i++) {
			ResourceShare resource = resourcesShares.get(i);
			if (!resource.tryAcquire(resourceRequestor)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void release() {
		releaseResources(resourcesShares.size());
	}

	private void releaseResources(int numResources) {
		for (int i = numResources-1; i >= 0; i--) {
			ResourceShare resource = resourcesShares.get(i);
			resource.release();
		}
	}

	@Override
	public void remove(ResourceRequestor resourceRequestor) {
		if (rejectedResourceIndex >= 0) {
			ResourceShare resource = resourcesShares.get(rejectedResourceIndex);
			resource.remove(resourceRequestor);
		}
	}
}
