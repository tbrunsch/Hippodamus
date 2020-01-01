package dd.kms.hippodamus.exceptions;

@FunctionalInterface
public interface ExceptionalSupplier<T, E extends Throwable>
{
	T get() throws E, InterruptedException;
}
