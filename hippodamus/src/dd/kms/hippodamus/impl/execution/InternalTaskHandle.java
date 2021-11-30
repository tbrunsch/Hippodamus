package dd.kms.hippodamus.impl.execution;

public interface InternalTaskHandle
{
	/**
	 * @return The ID of the associated task handle
	 */
	int getId();

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	void submit();

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	void stop();
}
