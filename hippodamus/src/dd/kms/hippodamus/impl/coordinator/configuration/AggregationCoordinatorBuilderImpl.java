package dd.kms.hippodamus.impl.coordinator.configuration;

import java.util.Map;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.AggregationCoordinatorBuilder;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.coordinator.AggregationCoordinatorImpl;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

public class AggregationCoordinatorBuilderImpl<S, R>
	extends CoordinatorBuilderBase<AggregationCoordinatorBuilder<S, R>, AggregationCoordinator<S, R>>
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
	AggregationCoordinator<S, R> createCoordinator(Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, boolean verifyDependencies) {
		return new AggregationCoordinatorImpl<>(aggregator, executorServiceWrappersByTaskType, logger, verifyDependencies);
	}
}
