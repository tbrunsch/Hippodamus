package dd.kms.hippodamus.api.coordinator.configuration;

import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;

import java.util.concurrent.ExecutorService;

public interface AggregationCoordinatorBuilder<S, R> extends ExecutionCoordinatorBuilder
{
	@Override
	AggregationCoordinatorBuilder<S, R> executorService(int taskType, ExecutorService executorService, boolean shutdownRequired);

	@Override
	AggregationCoordinatorBuilder<S, R> maximumParallelism(int taskType, int maxParallelism);

	@Override
	AggregationCoordinatorBuilder<S, R> logger(Logger logger);

	@Override
	AggregationCoordinatorBuilder<S, R> minimumLogLevel(LogLevel minimumLogLevel);

	@Override
	AggregationCoordinatorBuilder<S, R> verifyDependencies(boolean verifyDependencies);

	@Override
	AggregationCoordinatorBuilder<S, R> waitMode(WaitMode waitMode);

	@Override
	AggregationCoordinator<S, R> build();
}
