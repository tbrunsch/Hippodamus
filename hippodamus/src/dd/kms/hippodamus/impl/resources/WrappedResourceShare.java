package dd.kms.hippodamus.impl.resources;

import dd.kms.hippodamus.api.resources.Resource;

import java.util.function.Supplier;

/**
 * This class wraps a parameterized resource and a parameterized resource share supplier such that
 * {@link dd.kms.hippodamus.impl.handles.HandleImpl} can interact with an unparameterized resource share.
 */
class WrappedResourceShare<T> implements ResourceShare, Comparable<WrappedResourceShare<?>>
{
	private final Resource<T>	resource;
	private final Supplier<T>	resourceShareSupplier;

	private T					resourceShare;

	public WrappedResourceShare(Resource<T> resource, Supplier<T> resourceShareSupplier) {
		this.resource = resource;
		this.resourceShareSupplier = resourceShareSupplier;
	}

	@Override
	public boolean tryAcquire(Runnable tryAgainRunnable) {
		resourceShare = resourceShareSupplier.get();
		return resource.tryAcquire(resourceShare, tryAgainRunnable);
	}

	@Override
	public void release() {
		resource.release(resourceShare);
	}

	@Override
	public void remove(Runnable tryAgainRunnable) {
		resource.remove(tryAgainRunnable);
	}

	@Override
	public int compareTo(WrappedResourceShare<?> other) {
		int hashCodeLeft = System.identityHashCode(resource);
		int hashCodeRight = System.identityHashCode(other.resource);
		return Integer.compare(hashCodeLeft, hashCodeRight);
	}
}
