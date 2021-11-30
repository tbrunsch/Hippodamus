package dd.kms.hippodamus.api.exceptions;

import java.util.function.Supplier;

@FunctionalInterface
public interface StoppableExceptionalRunnable<T extends Throwable>
{
	void run(Supplier<Boolean> stopFlag) throws T;
}
