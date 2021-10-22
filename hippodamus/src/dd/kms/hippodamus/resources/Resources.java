package dd.kms.hippodamus.resources;

public class Resources
{
	public static Resource<Long> newCountableResource(String name, long initialCapacity) {
		return new CountableResource(name, initialCapacity);
	}
}
