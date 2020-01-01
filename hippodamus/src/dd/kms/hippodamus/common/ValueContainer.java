package dd.kms.hippodamus.common;

public class ValueContainer<T> implements ReadableValue<T>, WritableValue<T>
{
	private T value;

	public ValueContainer() {
		this(null);
	}

	public ValueContainer(T initialValue) {
		this.value = initialValue;
	}

	@Override
	public T get() {
		return value;
	}

	@Override
	public void set(T value) {
		this.value = value;
	}
}
