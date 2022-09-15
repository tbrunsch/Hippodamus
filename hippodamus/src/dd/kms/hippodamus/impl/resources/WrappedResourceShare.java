package dd.kms.hippodamus.impl.resources;

import com.google.common.base.Suppliers;
import dd.kms.hippodamus.api.resources.Resource;
import dd.kms.hippodamus.api.resources.ResourceRequestor;

import java.util.function.Supplier;

/**
 * This class wraps a parameterized resource and a parameterized resource share supplier such that
 * {@link dd.kms.hippodamus.impl.handles.HandleImpl} can interact with an unparameterized resource share.
 */
class WrappedResourceShare<T> implements ResourceShare, Comparable<WrappedResourceShare<?>>
{
	private final Resource<T>	resource;
	private final Supplier<T>	resourceShareSupplier;

	private boolean				addedPendingResourceShare;
	private boolean				acquiredResourceShare;

	public WrappedResourceShare(Resource<T> resource, Supplier<T> resourceShareSupplier) {
		this.resource = resource;
		this.resourceShareSupplier = Suppliers.memoize(resourceShareSupplier::get);
	}

	@Override
	public void addPendingResourceShare() {
		if (addedPendingResourceShare) {
			return;	// should not happen
		}
		resource.addPendingResourceShare(resourceShareSupplier.get());
		addedPendingResourceShare = true;
	}

	@Override
	public void removePendingResourceShare() {
		if (!addedPendingResourceShare) {
			return;	// should not happen
		}
		resource.removePendingResourceShare(resourceShareSupplier.get());
		addedPendingResourceShare = false;
	}

	@Override
	public boolean tryAcquire(ResourceRequestor resourceRequestor) {
		if (acquiredResourceShare) {
			return false;	// should not happen
		}
		acquiredResourceShare = resource.tryAcquire(resourceShareSupplier.get(), resourceRequestor);
		return acquiredResourceShare;
	}

	@Override
	public void release() {
		if (!acquiredResourceShare) {
			return;	// should not happen
		}
		resource.release(resourceShareSupplier.get());
		acquiredResourceShare = false;
	}

	@Override
	public void remove(ResourceRequestor resourceRequestor) {
		resource.remove(resourceRequestor);
	}

	@Override
	public int compareTo(WrappedResourceShare<?> other) {
		int hashCodeLeft = System.identityHashCode(resource);
		int hashCodeRight = System.identityHashCode(other.resource);
		return Integer.compare(hashCodeLeft, hashCodeRight);
	}
}
