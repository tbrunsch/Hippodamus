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

	public static <E extends Throwable> StoppableExceptionalCallable<Void, E> asCallable(StoppableExceptionalRunnable<E> runnable) {
		return stopFlag -> {
			runnable.run(stopFlag);
			return null;
		};
	}

	public static <E extends Throwable> StoppableExceptionalRunnable<E> asStoppable(ExceptionalRunnable<E> runnable) {
		return stopFlag -> runnable.run();
	}

	public static <V, E extends Throwable> StoppableExceptionalCallable<V, E> asStoppable(ExceptionalCallable<V, E> callable) {
		return stopFlag -> callable.call();
	}
}
