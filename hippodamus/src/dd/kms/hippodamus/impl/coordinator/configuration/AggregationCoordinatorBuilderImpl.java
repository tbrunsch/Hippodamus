package dd.kms.hippodamus.impl.coordinator.configuration;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.coordinator.AggregationCoordinatorImpl;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import java.util.Map;

public class AggregationCoordinatorBuilderImpl<S, R>
	extends CoordinatorBuilderBaseImpl<AggregationCoordinatorBuilder<S, R>, AggregationCoordinator<S, R>>
	implements AggregationCoordinatorBuilder<S, R>
{
	private final Aggregator<S, R> aggregator;

	public AggregationCoordinatorBuilderImpl(Aggregator<S, R> aggregator) {
		this.aggregator = aggregator;
	}

	@Override
	AggregationCoordinatorBuilder<S, R> getBuilder() {
		return this;
	}

	@Override
	AggregationCoordinator<S, R> createCoordinator(Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies, WaitMode waitMode) {
		return new AggregationCoordinatorImpl<>(aggregator, executorServiceWrappersByTaskType, logger, minimumLogLevel, verifyDependencies, waitMode);
	}
}
