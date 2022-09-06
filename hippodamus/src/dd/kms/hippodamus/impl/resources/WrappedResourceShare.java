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

	public WrappedResourceShare(Resource<T> resource, Supplier<T> resourceShareSupplier) {
		this.resource = resource;
		this.resourceShareSupplier = Suppliers.memoize(resourceShareSupplier::get);
	}

	@Override
	public void addPendingResourceShare() {
		resource.addPendingResourceShare(resourceShareSupplier.get());
	}

	@Override
	public void removePendingResourceShare() {
		resource.removePendingResourceShare(resourceShareSupplier.get());
	}

	@Override
	public boolean tryAcquire(ResourceRequestor resourceRequestor) {

		return resource.tryAcquire(resourceShareSupplier.get(), resourceRequestor);
	}

	@Override
	public void release() {
		resource.release(resourceShareSupplier.get());
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
