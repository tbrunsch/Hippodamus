package dd.kms.hippodamus.resources.memory;

/**
 * This class can be used to model controllable countable resources. See {@link CountableResource} for what we mean by
 * a countable resource. By "controllable" we mean that the user of the resource/framework has full control of when a
 * piece of the resource is acquired or released. Obviously, memory is no such resource: Even if you try to synchronize
 * your memory consumption with this resource instance, the available memory and the available size of this resource
 * instance will eventually diverge. Additionally, this implementation inherits the problems of
 * {@link AbstractCountableResource}.
 */
public class DefaultCountableResource extends AbstractCountableResource
{
	private final long	capacity;

	public DefaultCountableResource(String name, long capacity) {
		super(name);
		this.capacity = capacity;
	}

	@Override
	long getCapacity() {
		return capacity;
	}
}
