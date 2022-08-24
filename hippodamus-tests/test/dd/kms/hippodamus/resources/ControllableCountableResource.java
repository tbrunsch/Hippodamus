package dd.kms.hippodamus.resources;

class ControllableCountableResource extends AbstractCountableResource
{
	private final long	capacity;

	ControllableCountableResource(long capacity) {
		this.capacity = capacity;
	}

	@Override
	long getCapacity() {
		return capacity;
	}
}
