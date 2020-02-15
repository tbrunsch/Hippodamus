package dd.kms.hippodamus.execution;

public interface InternalTaskHandle
{
	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	void submit();

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	void stop();
}
