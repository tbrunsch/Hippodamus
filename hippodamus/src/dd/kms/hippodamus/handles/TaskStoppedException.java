package dd.kms.hippodamus.handles;

/**
 * This exception is thrown when a task waits for another task to complete
 * (see {@link Handle#join()}, {@link ResultHandle#get()}) and that task gets
 * stopped.
 */
public class TaskStoppedException extends RuntimeException
{
	public TaskStoppedException(String taskName) {
		super(taskName + " has been stopped");
	}
}
