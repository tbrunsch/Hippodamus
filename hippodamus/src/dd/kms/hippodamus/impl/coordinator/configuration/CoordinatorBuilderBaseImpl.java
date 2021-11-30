package dd.kms.hippodamus.impl.coordinator.configuration;

import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.api.logging.Loggers;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

abstract class CoordinatorBuilderBaseImpl<B extends ExecutionCoordinatorBuilder> implements ExecutionCoordinatorBuilder
{
	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private Logger										logger								= Loggers.NO_LOGGER;
	private LogLevel									minimumLogLevel						= LogLevel.STATE;
	private boolean										verifyDependencies					= false;
	private WaitMode									waitMode							= WaitMode.UNTIL_TERMINATION_REQUESTED;

	CoordinatorBuilderBaseImpl() {
		executorServiceWrappersByTaskType = new HashMap<>();
		executorServiceWrappersByTaskType.put(TaskType.REGULAR,	ExecutorServiceWrapper.commonForkJoinPoolWrapper(Integer.MAX_VALUE));
		executorServiceWrappersByTaskType.put(TaskType.IO,		ExecutorServiceWrapper.create(1, Integer.MAX_VALUE));
	}

	abstract B getBuilder();

	@Override
	public B executorService(int taskType, ExecutorService executorService, boolean shutdownRequired) {
		ExecutorServiceWrapper oldExecutorServiceWrapper = executorServiceWrappersByTaskType.get(taskType);
		ExecutorServiceWrapper executorServiceWrapper = oldExecutorServiceWrapper == null
			? ExecutorServiceWrapper.create(executorService, shutdownRequired, Integer.MAX_VALUE)
			: oldExecutorServiceWrapper.derive(executorService, shutdownRequired);
		executorServiceWrappersByTaskType.put(taskType, executorServiceWrapper);
		return getBuilder();
	}

	@Override
	public B maximumParallelism(int taskType, int maxParallelism) {
		if (maxParallelism <= 0) {
			throw new CoordinatorException("The maximum parallelism must be positive");
		}
		ExecutorServiceWrapper oldExecutorServiceWrapper = executorServiceWrappersByTaskType.get(taskType);
		ExecutorServiceWrapper executorServiceWrapper = oldExecutorServiceWrapper == null
			? ExecutorServiceWrapper.commonForkJoinPoolWrapper(maxParallelism)
			: oldExecutorServiceWrapper.derive(maxParallelism);
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

	CoordinatorConfiguration createConfiguration() {
		return new CoordinatorConfiguration(executorServiceWrappersByTaskType, logger, minimumLogLevel, verifyDependencies, waitMode);
	}
}
