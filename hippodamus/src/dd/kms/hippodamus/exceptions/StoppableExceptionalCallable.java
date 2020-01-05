package dd.kms.hippodamus.exceptions;

import java.util.function.Supplier;

@FunctionalInterface
public interface StoppableExceptionalCallable<V, E extends Throwable>
{
	V call(Supplier<Boolean> stopFlag) throws E;
}
