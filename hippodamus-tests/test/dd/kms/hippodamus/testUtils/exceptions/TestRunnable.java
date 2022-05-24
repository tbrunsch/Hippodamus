package dd.kms.hippodamus.testUtils.exceptions;

import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.states.HandleState;
import dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator;

public class TestRunnable<T extends Throwable> implements ExceptionalRunnable<T>
{
	private final BaseTestCoordinator<?> coordinator;
	private final ExceptionalRunnable<T>	wrappedRunnable;

	private volatile Handle					handle;

	public TestRunnable(BaseTestCoordinator<?> coordinator, ExceptionalRunnable<T> wrappedRunnable) {
		this.coordinator = coordinator;
		this.wrappedRunnable = wrappedRunnable;
	}

	public void setHandle(Handle handle) {
		this.handle = handle;
	}

	@Override
	public void run() throws T {
		while (handle == null);
		coordinator.handleState(handle, HandleState.STARTED, null);
		try {
			wrappedRunnable.run();
		} catch (Throwable t) {
			coordinator.handleState(handle, HandleState.TERMINATED_EXCEPTIONALLY, t);
			throw t;
		}
		if (Thread.currentThread().isInterrupted()) {
			// this will only work if the task did not clear the interrupted flag
			coordinator.handleState(handle, HandleState.STOPPED, null);
		}
		coordinator.handleState(handle, HandleState.COMPLETED, null);
	}
}
