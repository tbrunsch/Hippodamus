package dd.kms.hippodamus.impl.resources;

import dd.kms.hippodamus.api.resources.ResourceRequestor;

public interface ResourceShare
{
	void addPendingResourceShare();
	void removePendingResourceShare();
	boolean tryAcquire(ResourceRequestor resourceRequestor);

	/**
	 * Releases the resource share if it has been acquired. Otherwise, calling this method
	 * does not have any effect.
	 */
	void release();

	void remove(ResourceRequestor resourceRequestor);
}
