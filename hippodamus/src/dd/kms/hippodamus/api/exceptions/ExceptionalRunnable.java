package dd.kms.hippodamus.api.exceptions;

@FunctionalInterface
public interface ExceptionalRunnable<T extends Throwable>
{
	void run() throws T;
}
