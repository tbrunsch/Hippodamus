package dd.kms.hippodamus.exceptions;

@FunctionalInterface
public interface ExceptionalRunnable<E extends Throwable>
{
	void run() throws E;
}
