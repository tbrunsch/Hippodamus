package dd.kms.hippodamus.api.exceptions;

@FunctionalInterface
public interface ExceptionalCallable<V, T extends Throwable>
{
	V call() throws T;
}
