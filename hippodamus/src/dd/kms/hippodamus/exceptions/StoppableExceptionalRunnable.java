package dd.kms.hippodamus.exceptions;

import dd.kms.hippodamus.common.ReadableValue;

@FunctionalInterface
public interface StoppableExceptionalRunnable<E extends Throwable>
{
	void run(ReadableValue<Boolean> stopFlag) throws E, InterruptedException;
}
