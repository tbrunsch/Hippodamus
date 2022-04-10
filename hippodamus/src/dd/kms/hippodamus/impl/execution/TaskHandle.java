package dd.kms.hippodamus.impl.execution;

import dd.kms.hippodamus.impl.handles.ResultHandleImpl;

import java.util.concurrent.Future;
import java.util.function.Supplier;

public class TaskHandle
{
	private final ResultHandleImpl<?>		handle;
	private final ExecutorServiceWrapper	executorServiceWrapper;
	private Future<?>						future;

	TaskHandle(ResultHandleImpl<?> handle, ExecutorServiceWrapper executorServiceWrapper) {
		this.handle = handle;
		this.executorServiceWrapper = executorServiceWrapper;
	}

	/**
	 * @return The ID of the associated task handle
	 */
	public int getId() {
		return handle.getId();
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	public void submit() {
		if (future == null) {
			future = executorServiceWrapper.submitNow(handle);
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
