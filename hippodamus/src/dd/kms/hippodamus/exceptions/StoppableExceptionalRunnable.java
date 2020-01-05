package dd.kms.hippodamus.exceptions;

import java.util.function.Supplier;

@FunctionalInterface
public interface StoppableExceptionalRunnable<E extends Throwable>
{
	void run(Supplier<Boolean> stopFlag) throws E;
}
