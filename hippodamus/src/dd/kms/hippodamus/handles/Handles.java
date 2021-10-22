package dd.kms.hippodamus.handles;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.resources.ResourceShare;

import java.util.List;

public class Handles
{
	public static <T> ResultHandle<T> createResultHandle(InternalCoordinator coordinator, String taskName, int id, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies, List<ResourceShare<?>> requiredResourceShares, boolean stopped) {
		return new ResultHandleImpl<>(coordinator, taskName, id, executorServiceWrapper, callable, verifyDependencies, requiredResourceShares, stopped);
	}
}
