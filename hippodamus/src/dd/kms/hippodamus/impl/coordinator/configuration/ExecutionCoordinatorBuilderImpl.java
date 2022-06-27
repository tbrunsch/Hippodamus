package dd.kms.hippodamus.impl.coordinator.configuration;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import java.util.Map;

public class ExecutionCoordinatorBuilderImpl extends CoordinatorBuilderBase<ExecutionCoordinatorBuilder, ExecutionCoordinator> implements ExecutionCoordinatorBuilder
{
	@Override
	ExecutionCoordinatorBuilder getBuilder() {
		return this;
	}

	@Override
	ExecutionCoordinator createCoordinator(Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies) {
		return new ExecutionCoordinatorImpl(executorServiceWrappersByTaskType, logger, minimumLogLevel, verifyDependencies);
	}
}
