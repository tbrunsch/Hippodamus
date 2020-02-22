package dd.kms.hippodamus.exceptions;

@FunctionalInterface
public interface ExceptionalRunnable<T extends Throwable>
{
	void run() throws T;
}
