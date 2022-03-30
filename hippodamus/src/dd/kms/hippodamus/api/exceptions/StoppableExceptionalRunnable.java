package dd.kms.hippodamus.api.exceptions;

import java.util.function.Supplier;

/**
 * Same as {@link ExceptionalRunnable}, but the method {@link #run(Supplier)}
 * accepts a stop flag that can be checked in order to stop prematurely.
 */
@FunctionalInterface
public interface StoppableExceptionalRunnable<T extends Throwable>
{
	void run(Supplier<Boolean> stopFlag) throws T;
}
