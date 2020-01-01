package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.aggregation.Aggregator;

import java.util.concurrent.ExecutorService;

public class TaskCoordinators
{
	public static BasicTaskCoordinator createTaskCoordinator() {
		return new BasicTaskCoordinator();
	}

	public static <S, T> AggregatingTaskCoordinator<S, T> createAggregatingTaskCoordinator(Aggregator<S, T> aggregator) {
		return new AggregatingTaskCoordinatorImpl<>(aggregator);
	}

	// TODO: Remove this method; only used for current tests
	public static <S, T> AggregatingTaskCoordinator<S, T> createAggregatingTaskCoordinator(Aggregator<S, T> aggregator, ExecutorService executorService) {
		ExecutorServiceWrapper executorServiceWrapper = new ExecutorServiceWrapper(executorService, true);
		return new AggregatingTaskCoordinatorImpl<>(aggregator, executorServiceWrapper, executorServiceWrapper);
	}
}
