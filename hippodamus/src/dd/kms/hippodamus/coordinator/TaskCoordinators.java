package dd.kms.hippodamus.coordinator;

import com.google.common.collect.ImmutableMap;
import dd.kms.hippodamus.aggregation.Aggregator;

import java.util.concurrent.ExecutorService;

public class TaskCoordinators
{
	public static TaskCoordinator createTaskCoordinator() {
		return new BasicTaskCoordinator();
	}

	public static <S, T> AggregatingTaskCoordinator<S, T> createAggregatingTaskCoordinator(Aggregator<S, T> aggregator) {
		return new AggregatingTaskCoordinatorImpl<>(aggregator);
	}

	// TODO: Remove this method; only used for current tests
	public static <S, T> AggregatingTaskCoordinator<S, T> createAggregatingTaskCoordinator(Aggregator<S, T> aggregator, ExecutorService executorService) {
		ExecutorServiceWrapper executorServiceWrapper = new ExecutorServiceWrapper(executorService, true);
		ImmutableMap<Integer, ExecutorServiceWrapper> executorServiceWrappersById = ImmutableMap.<Integer, ExecutorServiceWrapper>builder()
			.put(ExecutorServiceIds.REGULAR, executorServiceWrapper)
			.put(ExecutorServiceIds.IO, executorServiceWrapper)
			.build();
		return new AggregatingTaskCoordinatorImpl<>(aggregator, executorServiceWrappersById);
	}
}
