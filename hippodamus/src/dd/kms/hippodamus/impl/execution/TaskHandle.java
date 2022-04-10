package dd.kms.hippodamus.impl.execution;

import java.util.concurrent.Future;
import java.util.function.Supplier;

public class TaskHandle
{
	private final int					id;
	private final Supplier<Future<?>>	submitter;
	private Future<?>					future;

	TaskHandle(int id, Supplier<Future<?>> submitter) {
		this.id = id;
		this.submitter = submitter;
	}

	/**
	 * @return The ID of the associated task handle
	 */
	public int getId() {
		return id;
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	public void submit() {
		if (future == null) {
			future = submitter.get();
		}
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	public void stop() {
		if (future != null) {
			future.cancel(true);
		}
	}
}
