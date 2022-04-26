package dd.kms.hippodamus.impl.exceptions;

import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;

/**
 * Utility class concerning exceptions
 */
public class Exceptions
{
	@SuppressWarnings("unchecked")
	public static <T extends Throwable> void throwUnchecked(Throwable t) throws T {
		throw (T) t;
	}

	public static <T extends Throwable> ExceptionalCallable<Void, T> asCallable(ExceptionalRunnable<T> runnable) {
		return () -> {
			runnable.run();
			return null;
		};
	}
}
