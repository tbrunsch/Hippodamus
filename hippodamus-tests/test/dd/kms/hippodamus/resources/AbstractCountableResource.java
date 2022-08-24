package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.execution.ExecutionController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

abstract class AbstractCountableResource implements CountableResource
{
	private long						totalReservedShareSize	= 0;

	private final List<ResourceShare>	postponedResourceShares	= new ArrayList<>();

	abstract long getCapacity();

	synchronized boolean tryAcquireShare(ResourceShare share) {
		long shareSize = share.getSize();
		long availableSize = getCapacity() - totalReservedShareSize;
		if (shareSize <= availableSize) {
			totalReservedShareSize += shareSize;
			return true;
		} else {
			postponedResourceShares.add(share);
			return false;
		}
	}

	synchronized void releaseShare(ResourceShare share) {
		long shareSize = share.getSize();
		totalReservedShareSize -= shareSize;
		long availableSize = getCapacity() - totalReservedShareSize;
		Iterator<ResourceShare> iter = postponedResourceShares.iterator();
		while (iter.hasNext()) {
			ResourceShare postponedShare = iter.next();
			long postponedShareSize = postponedShare.getSize();
			if (postponedShareSize <= availableSize) {
				availableSize -= postponedShareSize;
				postponedShare.getResourceRequest().run();
				iter.remove();
			}
		}
	}

	synchronized void removeResourceRequest(ResourceShare share) {
		postponedResourceShares.remove(share);
	}

	@Override
	public ExecutionController getShare(long size) {
		return new ResourceShare(size, this);
	}

	private static class ResourceShare implements ExecutionController
	{
		private final long						size;
		private final AbstractCountableResource	resource;

		private Runnable						resourceRequest;

		ResourceShare(long size, AbstractCountableResource resource) {
			this.size = size;
			this.resource = resource;
		}

		long getSize() {
			return size;
		}

		Runnable getResourceRequest() {
			return resourceRequest;
		}

		@Override
		public boolean permitExecution(Runnable submitLaterRunnable) {
			this.resourceRequest = submitLaterRunnable;
			return resource.tryAcquireShare(this);
		}

		@Override
		public void stop() {
			resource.removeResourceRequest(this);
		}

		@Override
		public void finishedExecution(boolean finishedSuccessfully) {
			resource.releaseShare(this);
		}
	}
}
