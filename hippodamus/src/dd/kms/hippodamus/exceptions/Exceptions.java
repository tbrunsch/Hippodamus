package dd.kms.hippodamus.exceptions;

public class Exceptions
{
	@SuppressWarnings("unchecked")
	public static <T extends Throwable> void throwUnchecked(Throwable t) throws T {
		throw (T) t;
	}

	public static <E extends Throwable> ExceptionalCallable<Void, E> asCallable(ExceptionalRunnable<E> runnable) {
		return () -> {
			runnable.run();
			return null;
		};
	}
}
