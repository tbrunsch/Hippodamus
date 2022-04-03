package dd.kms.hippodamus.impl.handles;

import dd.kms.hippodamus.api.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

public class Handles
{
	public static <T> ResultHandle<T> createResultHandle(ExecutionCoordinatorImpl coordinator, String taskName, int id, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies, boolean stopped) {
		return new ResultHandleImpl<>(coordinator, taskName, id, executorServiceWrapper, callable, verifyDependencies, stopped);
	}
}
