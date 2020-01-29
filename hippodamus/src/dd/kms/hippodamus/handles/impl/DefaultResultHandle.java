package dd.kms.hippodamus.handles.impl;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.handles.ResultHandle;

import java.util.concurrent.Future;

public class DefaultResultHandle<T> extends AbstractHandle implements ResultHandle<T>
{
	private final ExecutorServiceWrapper				executorServiceWrapper;
	private final StoppableExceptionalCallable<T, ?>	callable;

	private Future<T>									futureResult;
	private T											result;

	public DefaultResultHandle(ExecutionCoordinator coordinator, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies) {
		super(coordinator,  new HandleState(false, false), verifyDependencies);
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

	@Override
	boolean doWaitForFuture() throws Throwable {
		if (futureResult == null) {
			return false;
		}
		// TODO: How to handle RejectedExecutionException?
		futureResult.get();
		return true;
	}

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
