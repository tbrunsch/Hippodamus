package dd.kms.hippodamus.exceptions;

import dd.kms.hippodamus.common.ReadableValue;

@FunctionalInterface
public interface StoppableExceptionalCallable<V, E extends Throwable>
{
	V call(ReadableValue<Boolean> stopFlag) throws E, InterruptedException;
}
