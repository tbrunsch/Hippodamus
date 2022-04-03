package dd.kms.hippodamus.impl.coordinator.configuration;

import com.google.common.base.Preconditions;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.impl.logging.NoLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Base class for {@link ExecutionCoordinatorBuilderImpl} and {@link AggregationCoordinatorBuilderImpl}
 * to avoid implementing all methods of {@link dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder}
 * by delegating to the super method and returning a more concrete type.
 */
abstract class CoordinatorBuilderBase<B extends ExecutionCoordinatorBuilder, C extends ExecutionCoordinator> implements ExecutionCoordinatorBuilder
{
	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private Logger										logger								= NoLogger.LOGGER;
	private LogLevel									minimumLogLevel						= LogLevel.STATE;
	private boolean										verifyDependencies					= false;
	private WaitMode									waitMode							= WaitMode.UNTIL_TERMINATION_REQUESTED;

	CoordinatorBuilderBase() {
		executorServiceWrappersByTaskType = new HashMap<>();
		executorServiceWrappersByTaskType.put(TaskType.REGULAR,	ExecutorServiceWrapper.commonForkJoinPoolWrapper(Integer.MAX_VALUE));
		executorServiceWrappersByTaskType.put(TaskType.IO,		ExecutorServiceWrapper.create(1, Integer.MAX_VALUE));
	}

	abstract B getBuilder();
	abstract C createCoordinator(Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies, WaitMode waitMode);

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
		Preconditions.checkArgument(maxParallelism > 0, "Maximum parallelism must be positive");
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

	@Override
	public C build() {
		return createCoordinator(executorServiceWrappersByTaskType, logger, minimumLogLevel, verifyDependencies, waitMode);
	}
}
