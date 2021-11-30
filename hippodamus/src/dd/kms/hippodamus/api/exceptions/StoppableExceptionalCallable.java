package dd.kms.hippodamus.api.exceptions;

import java.util.function.Supplier;

@FunctionalInterface
public interface StoppableExceptionalCallable<V, T extends Throwable>
{
	V call(Supplier<Boolean> stopFlag) throws T;
}
