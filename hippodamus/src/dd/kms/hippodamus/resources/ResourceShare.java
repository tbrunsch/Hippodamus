package dd.kms.hippodamus.resources;

public class ResourceShare<S>
{
	private final Resource<S>	resource;
	private final S				share;
	private boolean				shareAcquired;

	public ResourceShare(Resource<S> resource, S share) {
		this.resource = resource;
		this.share = share;
	}

	public boolean acquire() {
		if (shareAcquired) {
			throw new IllegalStateException("The resource has already been acquired");
		}
		shareAcquired = resource.acquire(share);
		return shareAcquired;
	}

	public void release() {
		if (!shareAcquired) {
			throw new IllegalStateException("Cannot release a share that has not yet been acquired");
		}
		resource.release(share);
		shareAcquired = false;
	}

	public Resource<S> getResource() {
		return resource;
	}

	@Override
	public String toString() {
		return share + " of " + resource;
	}
}
