package dd.kms.hippodamus.api.exceptions;

import java.util.function.Supplier;

/**
 * Same as {@link ExceptionalCallable}, but the method {@link #call(Supplier)}
 * accepts a stop flag that can be checked in order to stop prematurely.
 */
@FunctionalInterface
public interface StoppableExceptionalCallable<V, T extends Throwable>
{
	V call(Supplier<Boolean> stopFlag) throws T;
}
