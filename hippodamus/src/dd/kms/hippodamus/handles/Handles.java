package dd.kms.hippodamus.handles;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;

public class Handles
{
	public static <T> ResultHandle<T> createResultHandle(InternalCoordinator coordinator, String taskName, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies, boolean stopped) {
		return new ResultHandleImpl<>(coordinator, taskName, executorServiceWrapper, callable, verifyDependencies, stopped);
	}
}
