package dd.kms.hippodamus.api.coordinator.configuration;

import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;

import java.util.concurrent.ExecutorService;

/**
 * Builder for an {@link AggregationCoordinator} that allows specifying
 * <ul>
 *     <li>the {@link ExecutorService} for tasks of a certain type,</li>
 *     <li>the maximum parallelism,</li>
 *     <li>whether to verify the specified dependencies,</li>
 *     <li>how long the {@code AggregationCoordinator}'s {@code close()} method waits, and</li>
 *     <li>a {@link Logger} and the minimum log level</li>
 * </ul>
 */
public interface AggregationCoordinatorBuilder<S, R> extends ExecutionCoordinatorBuilder
{
	@Override
	AggregationCoordinatorBuilder<S, R> executorService(TaskType taskType, ExecutorService executorService, boolean shutdownRequired);

	@Override
	AggregationCoordinatorBuilder<S, R> maximumParallelism(TaskType taskType, int maxParallelism);

	@Override
	AggregationCoordinatorBuilder<S, R> logger(Logger logger);

	@Override
	AggregationCoordinatorBuilder<S, R> minimumLogLevel(LogLevel minimumLogLevel);

	@Override
	AggregationCoordinatorBuilder<S, R> verifyDependencies(boolean verifyDependencies);

	@Override
	AggregationCoordinator<S, R> build();
}
