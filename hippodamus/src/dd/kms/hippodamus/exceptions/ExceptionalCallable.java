package dd.kms.hippodamus.exceptions;

@FunctionalInterface
public interface ExceptionalCallable<V, T extends Throwable>
{
	V call() throws T;
}
