package dd.kms.hippodamus.testUtils;

public class ValueReference<T>
{
	private volatile T	value;

	public ValueReference() {
		this(null);
	}

	public ValueReference(T initialValue) {
		this.value = initialValue;
	}

	public void set(T value) {
		this.value = value;
	}

	public T get() {
		return value;
	}
}
