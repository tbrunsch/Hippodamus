package dd.kms.hippodamus.testUtils.exceptions;

import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.states.HandleState;
import dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator;

public class TestCallable<V, T extends Throwable> implements ExceptionalCallable<V, T>
{
	private final BaseTestCoordinator<?> coordinator;
	private final ExceptionalCallable<V, T>	wrappedCallable;

	private Handle handle;

	public TestCallable(BaseTestCoordinator<?> coordinator, ExceptionalCallable<V, T> wrappedCallable) {
		this.coordinator = coordinator;
		this.wrappedCallable = wrappedCallable;
	}

	public void setHandle(Handle handle) {
		this.handle = handle;
	}

	@Override
	public V call() throws T {
		while (handle == null);
		coordinator.handleState(handle, HandleState.STARTED, null);
		V result;
		try {
			result = wrappedCallable.call();
		} catch (Throwable t) {
			coordinator.handleState(handle, HandleState.TERMINATED_EXCEPTIONALLY, t);
			throw t;
		}
		if (Thread.currentThread().isInterrupted()) {
			// this will only work if the task did not clear the interrupted flag
			coordinator.handleState(handle, HandleState.STOPPED, null);
		}
		coordinator.handleState(handle, HandleState.COMPLETED, null);
		return result;
	}
}
