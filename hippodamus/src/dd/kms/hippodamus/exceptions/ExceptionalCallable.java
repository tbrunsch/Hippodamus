package dd.kms.hippodamus.exceptions;

@FunctionalInterface
public interface ExceptionalCallable<V, E extends Throwable>
{
	V call() throws E;
}
