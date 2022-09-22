package dd.kms.hippodamus.impl.resources;

import dd.kms.hippodamus.api.resources.ResourceRequestor;

public interface ResourceShare
{
	/**
	 * Adds the resource share to the pending resource shares. It is guaranteed that
	 * this method is not called twice without a call to {@link #removePendingResourceShare()}
	 * inbetween.
	 */
	void addPendingResourceShare();

	/**
	 * Removes the resource share from the pending resource shares if it has been added before
	 * and not yet removed. Otherwise, calling this method does not have any effect.
	 */
	void removePendingResourceShare();

	/**
	 * Tries to acquire the resource share. It is guaranteed that this method is not called
	 * a second time after a successful call to this method without a call to {@link #release()}
	 * before.
	 */
	boolean tryAcquire(ResourceRequestor resourceRequestor);

	/**
	 * Releases the resource share if it has been acquired. Otherwise, calling this method
	 * does not have any effect.
	 */
	void release();

	/**
	 * Removed the specified {@code resourceRequestor} from the underlying resource.
	 * Calling this method multiple times should not have any effect.
	 */
	void remove(ResourceRequestor resourceRequestor);
}
