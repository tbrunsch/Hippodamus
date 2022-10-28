package dd.kms.hippodamus.impl.coordinator.configuration;

import com.google.common.base.Preconditions;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.impl.logging.NoLogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/**
 * Base class for {@link ExecutionCoordinatorBuilderImpl} and {@link AggregationCoordinatorBuilderImpl}
 * to avoid implementing all methods of {@link dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder}
 * by delegating to the super method and returning a more concrete type.
 */
abstract class CoordinatorBuilderBase<B extends ExecutionCoordinatorBuilder, C extends ExecutionCoordinator> implements ExecutionCoordinatorBuilder
{
	private final Map<TaskType, ExecutorService>		executorServicesByTaskType			= new HashMap<>();
	private final Set<TaskType> 						taskTypesThatRequireShutdown		= new HashSet<>();
	private final Map<TaskType, Integer>				maximumParallelismByTaskType		= new HashMap<>();
	private Logger										logger								= NoLogger.LOGGER;
	private boolean										verifyDependencies					= false;

	CoordinatorBuilderBase() {
		maximumParallelism(TaskType.COMPUTATIONAL, Integer.MAX_VALUE);
		executorService(TaskType.BLOCKING, Executors.newWorkStealingPool(1), true);
	}

	abstract B getBuilder();
	abstract C createCoordinator(Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, boolean verifyDependencies);

	@Override
	public B executorService(TaskType taskType, ExecutorService executorService, boolean shutdownRequired) {
		executorServicesByTaskType.put(taskType, executorService);
		if (shutdownRequired) {
			taskTypesThatRequireShutdown.add(taskType);
		} else {
			taskTypesThatRequireShutdown.remove(taskType);
		}
		return getBuilder();
	}

	@Override
	public B maximumParallelism(TaskType taskType, int maxParallelism) {
		Preconditions.checkArgument(maxParallelism > 0, "Maximum parallelism must be positive");
		maximumParallelismByTaskType.put(taskType, maxParallelism);
		return getBuilder();
	}

	@Override
	public B logger(Logger logger) {
		this.logger = logger;
		return getBuilder();
	}

	@Override
	public B verifyDependencies(boolean verifyDependencies) {
		this.verifyDependencies = verifyDependencies;
		return getBuilder();
	}

	@Override
	public C build() {
		Set<TaskType> taskTypes = getConfiguredTaskTypes();
		Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType = new HashMap<>();
		for (TaskType taskType : taskTypes) {
			ExecutorService executorService = executorServicesByTaskType.get(taskType);
			if (executorService == null) {
				executorService = ForkJoinPool.commonPool();
			}
			boolean shutdownRequired = taskTypesThatRequireShutdown.contains(taskType);
			int maxParallelism = maximumParallelismByTaskType.getOrDefault(taskType, Integer.MAX_VALUE);
			ExecutorServiceWrapper executorServiceWrapper = new ExecutorServiceWrapper(executorService, shutdownRequired, maxParallelism);
			executorServiceWrappersByTaskType.put(taskType, executorServiceWrapper);
		}

		return createCoordinator(executorServiceWrappersByTaskType, logger, verifyDependencies);
	}

	private Set<TaskType> getConfiguredTaskTypes() {
		Set<TaskType> taskTypes = new HashSet<>();
		taskTypes.addAll(executorServicesByTaskType.keySet());
		taskTypes.addAll(maximumParallelismByTaskType.keySet());
		return taskTypes;
	}
}
