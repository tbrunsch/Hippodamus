package dd.kms.hippodamus.resources.internalmanagement;

import dd.kms.hippodamus.api.resources.Resource;
import dd.kms.hippodamus.api.resources.ResourceRequestor;
import dd.kms.hippodamus.testUtils.events.TestEventManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class TestResource implements Resource<TaskDescription>, Cloneable
{
	private final long					capacity;
	private final TestEventManager		eventManager;

	private final long[]				totalPendingResourceShares	= {0L, 0L};
	private final long[]				acquiredResourceShares		= {0L, 0L};
	private final List<ResourceRequest>	postponedResourceRequests	= new ArrayList<>();

	TestResource(long capacity, TestEventManager eventManager) {
		this.capacity = capacity;
		this.eventManager = eventManager;
	}

	TestEventManager getEventManager() {
		return eventManager;
	}

	@Override
	public synchronized void addPendingResourceShare(TaskDescription resourceShare) {
		PendingResourceEvent event = new PendingResourceEvent(resourceShare, PendingResourceEvent.State.ADDED);
		eventManager.fireEvent(event);
		int threadIndex = resourceShare.getThreadIndex();
		totalPendingResourceShares[threadIndex] += resourceShare.getRequiredResourceSize();
	}

	@Override
	public synchronized void removePendingResourceShare(TaskDescription resourceShare) {
		PendingResourceEvent event = new PendingResourceEvent(resourceShare, PendingResourceEvent.State.REMOVED);
		eventManager.fireEvent(event);
		int threadIndex = resourceShare.getThreadIndex();
		totalPendingResourceShares[threadIndex] -= resourceShare.getRequiredResourceSize();
		retryPostponedResourceRequests();
	}

	@Override
	public synchronized boolean tryAcquire(TaskDescription resourceShare, ResourceRequestor resourceRequestor) {
		long requiredResourceSize = resourceShare.getRequiredResourceSize();
		if (this.acquiredResourceShares[0] + this.acquiredResourceShares[1] + requiredResourceSize <= capacity) {
			ResourceEvent event = new ResourceEvent(resourceShare, ResourceEvent.State.ACQUIRED);
			eventManager.fireEvent(event);
			int threadIndex = resourceShare.getThreadIndex();
			acquiredResourceShares[threadIndex] += requiredResourceSize;
			return true;
		} else {
			ResourceEvent event = new ResourceEvent(resourceShare, ResourceEvent.State.REJECTED);
			eventManager.fireEvent(event);
			ResourceRequest resourceRequest = new ResourceRequest(resourceShare, resourceRequestor);
			postponedResourceRequests.add(resourceRequest);
			return false;
		}
	}

	@Override
	public synchronized void release(TaskDescription resourceShare) {
		ResourceEvent event = new ResourceEvent(resourceShare, ResourceEvent.State.RELEASED);
		eventManager.fireEvent(event);
		int threadIndex = resourceShare.getThreadIndex();
		acquiredResourceShares[threadIndex] -= resourceShare.getRequiredResourceSize();
		retryPostponedResourceRequests();
	}

	@Override
	public synchronized void remove(ResourceRequestor resourceRequestor) {
		ResourceRequestorRemovedEvent event = new ResourceRequestorRemovedEvent(resourceRequestor.getHandle());
		eventManager.fireEvent(event);
		postponedResourceRequests.removeIf(postponedResourceRequest -> postponedResourceRequest.getResourceRequestor() == resourceRequestor);
	}

	private void retryPostponedResourceRequests() {
		long availableSize = capacity
			- acquiredResourceShares[0] - acquiredResourceShares[1]
			- totalPendingResourceShares[0] - totalPendingResourceShares[1];
		Iterator<ResourceRequest> iter = postponedResourceRequests.iterator();
		while (iter.hasNext()) {
			ResourceRequest postponedResourceRequest = iter.next();
			long requiredResourceSize = postponedResourceRequest.getResourceShare().getRequiredResourceSize();
			if (requiredResourceSize <= availableSize) {
				availableSize -= requiredResourceSize;
				ResourceRequestor resourceRequestor = postponedResourceRequest.getResourceRequestor();
				resourceRequestor.retryRequest();
				iter.remove();
			}
		}
	}

	synchronized long getTotalPendingResourceShares(int threadIndex) {
		return totalPendingResourceShares[threadIndex];
	}

	synchronized long getAcquiredResourceShares(int threadIndex) {
		return acquiredResourceShares[threadIndex];
	}

	synchronized List<ResourceRequest> getPostponedResourceRequests() {
		return new ArrayList<>(postponedResourceRequests);
	}

	@Override
	public synchronized TestResource clone() {
		TestResource copy = new TestResource(capacity, eventManager);
		copy.totalPendingResourceShares[0] = totalPendingResourceShares[0];
		copy.totalPendingResourceShares[1] = totalPendingResourceShares[1];
		copy.acquiredResourceShares[0] = acquiredResourceShares[0];
		copy.acquiredResourceShares[1] = acquiredResourceShares[1];
		copy.postponedResourceRequests.addAll(postponedResourceRequests);
		return copy;
	}
}
