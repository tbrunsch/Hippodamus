package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.resources.ResourceRequestor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class contains common behavior of different implementations of {@link CountableResource}.
 * It is just a simple implementation with the following drawbacks:
 * <ul>
 *     <li>
 *         Starvation: There is a chance that a task with a high resource requirement cannot be resubmitted because new
 *         tasks with a relatively low resource requirement appear all the time. It might be the case that their resource
 *         request can be satisfied whereas the big task's resource is always too large because the smaller tasks consume
 *         too much of the resource in total. Even if the big task is resubmitted, it is not guaranteed that its resource
 *         request will be accepted due to the same reasons.
 *     </li>
 * </ul>
 */
public abstract class AbstractCountableResource implements CountableResource
{
	private final String				name;

	private long						totalSubmittedShareSized	= 0;
	private long						totalReservedShareSize		= 0;
	private final List<ResourceRequest>	postponedResourceRequests	= new ArrayList<>();

	protected abstract long getCapacity();

	protected AbstractCountableResource(String name) {
		this.name = name;
	}

	@Override
	public synchronized void addPendingResourceShare(Long resourceShare) {
		totalSubmittedShareSized += resourceShare;
	}

	@Override
	public synchronized void removePendingResourceShare(Long resourceShare) {
		totalSubmittedShareSized -= resourceShare;
		resubmitPostponedResourceRequests();
	}

	@Override
	public synchronized boolean tryAcquire(Long shareSize, ResourceRequestor resourceRequestor) {
		long availableSize = getCapacity() - totalReservedShareSize;
		if (shareSize <= availableSize) {
			totalReservedShareSize += shareSize;
			return true;
		} else {
			ResourceRequest resourceRequest = new ResourceRequest(shareSize, resourceRequestor);
			postponedResourceRequests.add(resourceRequest);
			return false;
		}
	}

	@Override
	public synchronized void release(Long shareSize) {
		totalReservedShareSize -= shareSize;
		resubmitPostponedResourceRequests();
	}

	@Override
	public synchronized void remove(ResourceRequestor resourceRequestor) {
		Iterator<ResourceRequest> iter = postponedResourceRequests.iterator();
		while (iter.hasNext()) {
			ResourceRequest postponedRequest = iter.next();
			if (postponedRequest.getRequestor() == resourceRequestor) {
				iter.remove();
				return;
			}
		}
	}

	private void resubmitPostponedResourceRequests() {
		long availableSize = getCapacity() - totalReservedShareSize - totalSubmittedShareSized;
		Iterator<ResourceRequest> iter = postponedResourceRequests.iterator();
		while (iter.hasNext()) {
			ResourceRequest postponedRequest = iter.next();
			long postponedShareSize = postponedRequest.getShareSize();
			if (postponedShareSize <= availableSize) {
				availableSize -= postponedShareSize;
				postponedRequest.getRequestor().retryRequest();
				iter.remove();
			}
		}
	}

	@Override
	public String toString() {
		return name;
	}

	private static class ResourceRequest
	{
		private final long				shareSize;
		private final ResourceRequestor requestor;

		ResourceRequest(long shareSize, ResourceRequestor requestor) {
			this.shareSize = shareSize;
			this.requestor = requestor;
		}

		long getShareSize() {
			return shareSize;
		}

		ResourceRequestor getRequestor() {
			return requestor;
		}
	}
}
