package dd.kms.hippodamus.handles.impl;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.handles.ResultHandle;

import java.util.concurrent.Future;

public class DefaultResultHandle<T> extends AbstractHandle implements ResultHandle<T>
{
	private final ExecutorServiceWrapper				executorServiceWrapper;
	private final StoppableExceptionalCallable<T, ?>	callable;

	private volatile Future<T>							futureResult;
	private volatile T									result;

	public DefaultResultHandle(InternalCoordinator coordinator, String taskName, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies) {
		super(coordinator, taskName, new HandleState(false, false), verifyDependencies);
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
	}

	@Override
	void doSubmit() {
		futureResult = executorServiceWrapper.submit(this::executeCallable);
	}

	@Override
	void doStop() {
		futureResult.cancel(true);
	}

	// TODO: Throw an exception if state.getException() != null?
	@Override
	public T get() {
		join();
		return result;
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
		// TODO: Abort execution if handle stopped?
		if (!onStartExecution()) {
			return null;
		}
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
