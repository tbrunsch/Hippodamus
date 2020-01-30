package dd.kms.hippodamus.handles.impl;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.exceptions.CoordinatorException;
import dd.kms.hippodamus.handles.ResultHandle;

public class StoppedResultHandle<T> extends AbstractHandle implements ResultHandle<T>
{
	public StoppedResultHandle(ExecutionCoordinator coordinator, boolean verifyDependencies) {
		super(coordinator, new HandleState(false, true), verifyDependencies);
	}

	@Override
	public T get() {
		return null;
	}

	@Override
	void doSubmit() {
		throw new CoordinatorException("Internal error: A stopped handle should not be submitted");
	}

	@Override
	void doStop() {
		throw new CoordinatorException("Internal error: A stopped handle should not be stopped again");
	}

	@Override
	boolean doWaitForFuture() {
		return false;
	}
}
