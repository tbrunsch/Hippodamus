package dd.kms.hippodamus.handles.impl;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.handles.ResultHandle;

public class StoppedResultHandle<T> extends AbstractHandle implements ResultHandle<T>
{
	public StoppedResultHandle(ExecutionCoordinator coordinator) {
		super(coordinator, new HandleState(false, true));
	}

	@Override
	public T get() {
		return null;
	}

	@Override
	void doSubmit() {
		throw new IllegalStateException("Internal error: A stopped handle should not be submitted");
	}

	@Override
	void doStop() {
		throw new IllegalStateException("Internal error: A stopped handle should not be stopped again");
	}
}
