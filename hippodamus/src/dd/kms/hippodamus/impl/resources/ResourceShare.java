package dd.kms.hippodamus.impl.resources;

import dd.kms.hippodamus.api.resources.ResourceRequestor;

public interface ResourceShare
{
	void addPendingResourceShare();
	void removePendingResourceShare();
	boolean tryAcquire(ResourceRequestor resourceRequestor);
	void release();
	void remove(ResourceRequestor resourceRequestor);
}
