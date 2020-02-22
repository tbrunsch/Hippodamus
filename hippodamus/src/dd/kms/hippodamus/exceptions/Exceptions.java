package dd.kms.hippodamus.exceptions;

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

	public static <T extends Throwable> StoppableExceptionalCallable<Void, T> asCallable(StoppableExceptionalRunnable<T> runnable) {
		return stopFlag -> {
			runnable.run(stopFlag);
			return null;
		};
	}

	public static <T extends Throwable> StoppableExceptionalRunnable<T> asStoppable(ExceptionalRunnable<T> runnable) {
		return stopFlag -> runnable.run();
	}

	public static <V, T extends Throwable> StoppableExceptionalCallable<V, T> asStoppable(ExceptionalCallable<V, T> callable) {
		return stopFlag -> callable.call();
	}
}
