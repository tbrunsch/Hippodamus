package dd.kms.hippodamus.api.exceptions;

/**
 * Same as {@link Runnable}, but {@link #run()} may throw an exception. The generic
 * parameter specifies which type of exception may be thrown.
 */
@FunctionalInterface
public interface ExceptionalRunnable<T extends Throwable>
{
	void run() throws T;
}
