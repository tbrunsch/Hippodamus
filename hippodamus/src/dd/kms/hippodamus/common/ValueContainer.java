package dd.kms.hippodamus.common;

import java.util.function.Supplier;

public class ValueContainer<T> implements Supplier<T>, WritableValue<T>
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
