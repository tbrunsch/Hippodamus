package dd.kms.hippodamus.resources.memory;

import dd.kms.hippodamus.api.resources.ResourceRequestor;
import dd.kms.hippodamus.resources.CountableResource;

class UnlimitedCountableResource implements CountableResource
{
	static final CountableResource	RESOURCE	= new UnlimitedCountableResource();

	@Override
	public void addPendingResourceShare(Long resourceShare) {
		/* nothing to do */
	}

	@Override
	public void removePendingResourceShare(Long resourceShare) {
		/* nothing to do*/
	}

	@Override
	public boolean tryAcquire(Long resourceShare, ResourceRequestor resourceRequestor) {
		return true;
	}

	@Override
	public void release(Long resourceShare) {
		/* nothing to do */
	}

	@Override
	public void remove(ResourceRequestor resourceRequestor) {
		/* nothing to do */
	}

	@Override
	public String toString() {
		return "Unlimited countable resource";
	}
}
