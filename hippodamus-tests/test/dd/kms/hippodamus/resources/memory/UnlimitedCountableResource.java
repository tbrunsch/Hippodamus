package dd.kms.hippodamus.resources.memory;

class UnlimitedCountableResource implements CountableResource
{
	static final CountableResource	RESOURCE	= new UnlimitedCountableResource();

	@Override
	public boolean tryAcquire(Long resourceShare, Runnable tryAgainRunnable) {
		return true;
	}

	@Override
	public void release(Long resourceShare) {
		/* nothing to do */
	}

	@Override
	public void remove(Runnable tryAgainRunnable) {
		/* nothing to do */
	}

	@Override
	public String toString() {
		return "Unlimited countable resource";
	}
}
