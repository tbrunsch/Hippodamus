package dd.kms.hippodamus.handles.impl;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.concurrent.Future;

public class DefaultResultHandle<T> extends AbstractHandle implements ResultHandle<T>
{
	private final ExecutorServiceWrapper				executorServiceWrapper;
	private final StoppableExceptionalCallable<T, ?>	callable;
	private final boolean								verifyDependencies;

	private Future<T>									futureResult;
	private T											result;

	public DefaultResultHandle(ExecutionCoordinator coordinator, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies) {
		super(coordinator,  new HandleState(false, false));
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
		this.verifyDependencies = verifyDependencies;
	}

	@Override
	void doSubmit() {
		futureResult = executorServiceWrapper.submit(this::executeCallable);
	}

	@Override
	void doStop() {
		futureResult.cancel(true);
	}

	@Override
	public T get() {
		if (hasCompleted() || isCompleting()) {
			return result;
		}
		if (verifyDependencies) {
			ExecutionCoordinator coordinator = getExecutionCoordinator();
			coordinator.log(LogLevel.INTERNAL_ERROR, this, "Accessing handle value although it has not completed");
			return null;
		}
		while (true) {
			if (hasCompleted() || isCompleting()) {
				return result;
			}
			if (hasStopped()) {
				return null;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				stop();
				return null;
			}
		}
	}

	private void setResult(T value) {
		synchronized (state) {
			if (state.isFlagSet(StateFlag.COMPLETED) || state.getException() != null) {
				return;
			}
			result = value;
			markAsCompleted();
		}
	}

	private T executeCallable() {
		try {
			T result = callable.call(this::hasStopped);
			setResult(result);
			return result;
		} catch (Throwable throwable) {
			setException(throwable);
			return null;
		}
	}
}
