package dd.kms.hippodamus.testUtils.exceptions;

import com.google.common.base.Preconditions;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator;
import dd.kms.hippodamus.testUtils.states.HandleState;

public class TestCallable<V, T extends Throwable> implements ExceptionalCallable<V, T>
{
	private final BaseTestCoordinator<?>	coordinator;
	private final ExceptionalCallable<V, T>	wrappedCallable;

	private volatile Handle					handle;

	public TestCallable(BaseTestCoordinator<?> coordinator, ExceptionalCallable<V, T> wrappedCallable) {
		this.coordinator = coordinator;
		this.wrappedCallable = wrappedCallable;
	}

	public void setHandle(Handle handle) {
		this.handle = handle;
	}

	@Override
	public V call() throws T {
		Preconditions.checkState(handle != null, "Handle should have been set before start of execution");
		coordinator.handleState(handle, HandleState.STARTED);
		V result;
		try {
			result = wrappedCallable.call();
		} catch (Throwable t) {
			coordinator.handleState(handle, HandleState.TERMINATED_EXCEPTIONALLY);
			coordinator.setException(handle, t);
			throw t;
		}
		if (Thread.currentThread().isInterrupted()) {
			// this will only work if the task did not clear the interrupted flag
			coordinator.handleState(handle, HandleState.STOPPED);
		}
		coordinator.handleState(handle, HandleState.COMPLETED);
		return result;
	}
}
