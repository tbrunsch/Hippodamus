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
	private Supplier<T>			resourceShareSupplier;

	private boolean				resourceShareDetermined;
	private T					resourceShare;

	public WrappedResourceShare(Resource<T> resource, Supplier<T> resourceShareSupplier) {
		this.resource = resource;
		this.resourceShareSupplier = resourceShareSupplier;
	}

	@Override
	public void addPendingResourceShare() {
		resource.addPendingResourceShare(getResourceShare());
	}

	@Override
	public void removePendingResourceShare() {
		resource.removePendingResourceShare(getResourceShare());
	}

	@Override
	public boolean tryAcquire(Runnable tryAgainRunnable) {

		return resource.tryAcquire(getResourceShare(), tryAgainRunnable);
	}

	@Override
	public void release() {
		resource.release(getResourceShare());
	}

	@Override
	public void remove(Runnable tryAgainRunnable) {
		resource.remove(tryAgainRunnable);
	}

	private T getResourceShare() {
		if (!resourceShareDetermined) {
			resourceShare = resourceShareSupplier.get();
			resourceShareSupplier = null;
			resourceShareDetermined = true;
		}
		return resourceShare;
	}

	@Override
	public int compareTo(WrappedResourceShare<?> other) {
		int hashCodeLeft = System.identityHashCode(resource);
		int hashCodeRight = System.identityHashCode(other.resource);
		return Integer.compare(hashCodeLeft, hashCodeRight);
	}
}
