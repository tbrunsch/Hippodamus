package dd.kms.hippodamus.resources;

class MemoryResource extends AbstractCountableResource
{
	static final CountableResource	RESOURCE	= new MemoryResource();

	@Override
	long getCapacity() {
		return MemoryUtils.getAvailableMemory();
	}
}
