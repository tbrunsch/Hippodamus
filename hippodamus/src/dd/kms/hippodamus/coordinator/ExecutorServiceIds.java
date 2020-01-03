package dd.kms.hippodamus.coordinator;

/**
 * This class contains 2 predefined IDs for {@link java.util.concurrent.ExecutorService}s:
 */
public class ExecutorServiceIds
{
	/**
	 * This ID refers to the executor service that is used for regular (non-IO) tasks.
	 */
	public static final int	REGULAR	= -1;

	/**
	 * This ID refers to the executor service that is used for IO-tasks.
	 */
	public static final int	IO		= -2;
}
