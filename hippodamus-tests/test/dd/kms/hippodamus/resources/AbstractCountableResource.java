package dd.kms.hippodamus.resources;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

abstract class AbstractCountableResource implements CountableResource
{
	private long						totalReservedShareSize	= 0;
	private final List<ResourceRequest>	postponedResourceRequests	= new ArrayList<>();

	abstract long getCapacity();

	@Override
	public synchronized boolean tryAcquire(Long shareSize, Runnable tryAgainRunnable) {
		long availableSize = getCapacity() - totalReservedShareSize;
		if (shareSize <= availableSize) {
			totalReservedShareSize += shareSize;
			return true;
		} else {
			ResourceRequest resourceRequest = new ResourceRequest(shareSize, tryAgainRunnable);
			postponedResourceRequests.add(resourceRequest);
			return false;
		}
	}

	@Override
	public synchronized void release(Long shareSize) {
		totalReservedShareSize -= shareSize;
		long availableSize = getCapacity() - totalReservedShareSize;
		Iterator<ResourceRequest> iter = postponedResourceRequests.iterator();
		while (iter.hasNext()) {
			ResourceRequest postponedRequest = iter.next();
			long postponedShareSize = postponedRequest.getShareSize();
			if (postponedShareSize <= availableSize) {
				availableSize -= postponedShareSize;
				postponedRequest.getTryAgainRunnable().run();
				iter.remove();
			}
		}
	}

	@Override
	public synchronized void remove(Runnable tryAgainRunnable) {
		Iterator<ResourceRequest> iter = postponedResourceRequests.iterator();
		while (iter.hasNext()) {
			ResourceRequest postponedRequest = iter.next();
			if (postponedRequest.getTryAgainRunnable() == tryAgainRunnable) {
				iter.remove();
				return;
			}
		}
	}

	private static class ResourceRequest
	{
		private final long		shareSize;
		private final Runnable	tryAgainRunnable;

		ResourceRequest(long shareSize, Runnable tryAgainRunnable) {
			this.shareSize = shareSize;
			this.tryAgainRunnable = tryAgainRunnable;
		}

		long getShareSize() {
			return shareSize;
		}

		Runnable getTryAgainRunnable() {
			return tryAgainRunnable;
		}
	}
}
