package dd.kms.hippodamus.resources;

class CountableResource implements Resource<Long>
{
	private final String	name;
	private long			availableAmount;

	CountableResource(String name, long initialCapacity) {
		this.name = name;
		availableAmount = initialCapacity;
	}

	@Override
	public boolean acquire(Long share) {
		if (share > availableAmount) {
			return false;
		}
		availableAmount -= share;
		return true;
	}

	@Override
	public void release(Long share) {
		availableAmount += share;
	}

	@Override
	public String toString() {
		return name;
	}
}
