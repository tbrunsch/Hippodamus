package dd.kms.hippodamus.coordinator.configuration;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.ExecutionCoordinatorImpl;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.logging.LogLevel;
import dd.kms.hippodamus.logging.Logger;
import dd.kms.hippodamus.logging.Loggers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class ExecutionCoordinatorBuilderImpl<B extends ExecutionCoordinatorBuilder<B>> implements ExecutionCoordinatorBuilder<B>
{
	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private Logger										logger								= Loggers.NO_LOGGER;
	private LogLevel									minimumLogLevel						= LogLevel.STATE;
	private boolean										verifyDependencies					= false;
	private WaitMode									waitMode							= WaitMode.UNTIL_TERMINATION_REQUESTED;

	public ExecutionCoordinatorBuilderImpl() {
		executorServiceWrappersByTaskType = new HashMap<>();
		executorServiceWrappersByTaskType.put(TaskType.REGULAR,	ExecutorServiceWrapper.COMMON_FORK_JOIN_POOL_WRAPPER);
		executorServiceWrappersByTaskType.put(TaskType.IO,		ExecutorServiceWrapper.create(1));
	}

	@Override
	public B executorService(int taskType, ExecutorService executorService, boolean shutdownRequired) {
		ExecutorServiceWrapper executorServiceWrapper = ExecutorServiceWrapper.create(executorService, shutdownRequired);
		executorServiceWrappersByTaskType.put(taskType, executorServiceWrapper);
		return getBuilder();
	}

	@Override
	public B logger(Logger logger) {
		this.logger = logger;
		return getBuilder();
	}

	@Override
	public B minimumLogLevel(LogLevel minimumLogLevel) {
		this.minimumLogLevel = minimumLogLevel;
		return getBuilder();
	}

	@Override
	public B verifyDependencies(boolean verifyDependencies) {
		this.verifyDependencies = verifyDependencies;
		return getBuilder();
	}
	@Override
	public B waitMode(WaitMode waitMode) {
		this.waitMode = waitMode;
		return getBuilder();
	}

	@Override
	public ExecutionCoordinator build() {
		CoordinatorConfiguration configuration = createConfiguration();
		return new ExecutionCoordinatorImpl(configuration);
	}

	CoordinatorConfiguration createConfiguration() {
		return new CoordinatorConfiguration(executorServiceWrappersByTaskType, logger, minimumLogLevel, verifyDependencies, waitMode);
	}

	private B getBuilder() {
		return (B) this;
	}
}
