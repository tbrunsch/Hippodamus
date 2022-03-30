package dd.kms.hippodamus.api.exceptions;

/**
 * Same as {@link java.util.concurrent.Callable}, but with a generic parameter
 * that specifies which exception {@link #call()} may throw. This exception may
 * also be an unchecked exception.
 */
@FunctionalInterface
public interface ExceptionalCallable<V, T extends Throwable>
{
	V call() throws T;
}
