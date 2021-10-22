package dd.kms.hippodamus.execution;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.resources.ResourceShare;

import java.util.List;

public interface InternalTaskHandle extends Runnable
{
	/**
	 * @return The ID of the associated task handle
	 */
	int getId();

	/**
	 * @return A list of resource shares required by the task
	 */
	List<ResourceShare<?>> getRequiredResourceShares();

	/**
	 * @return The execution coordinator for that task
	 */
	InternalCoordinator getCoordinator();

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	void submit();

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	void stop();
}
