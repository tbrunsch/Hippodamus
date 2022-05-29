package dd.kms.hippodamus.testUtils.coordinator;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import dd.kms.hippodamus.testUtils.states.HandleState;

public abstract class BaseTestCoordinator<C extends ExecutionCoordinator> implements ExecutionCoordinator
{
	final C										wrappedCoordinator;
	private final TestEventManager				eventManager;

	BaseTestCoordinator(C wrappedCoordinator, TestEventManager eventManager) {
		this.wrappedCoordinator = wrappedCoordinator;
		this.eventManager = eventManager;
		eventManager.encounteredEvent(new CoordinatorEvent(CoordinatorState.STARTED));
	}

	private void encounteredEvent(TestEvent event) {
		eventManager.encounteredEvent(event);
	}

	public void handleState(Handle handle, HandleState state) {
		encounteredEvent(new HandleEvent(handle, state));
	}

	public void setException(Handle handle, Throwable t) {
		eventManager.setException(handle, t);
	}

	@Override
	public void permitTaskSubmission(boolean permit) {
		wrappedCoordinator.permitTaskSubmission(permit);
	}

	@Override
	public void stop() {
		wrappedCoordinator.stop();
		encounteredEvent(new CoordinatorEvent(CoordinatorState.STOPPED));
	}

	@Override
	public void checkException() {
		wrappedCoordinator.checkException();
	}

	@Override
	public void close() {
		encounteredEvent(new CoordinatorEvent(CoordinatorState.CLOSING));

		wrappedCoordinator.close();

		encounteredEvent(new CoordinatorEvent(CoordinatorState.CLOSED));
	}

	@Override
	public <T extends Throwable> Handle execute(ExceptionalRunnable<T> runnable) throws T {
		return configure().execute(runnable);
	}

	@Override
	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) throws T {
		return configure().execute(callable);
	}
}
