package dd.kms.hippodamus.coordinator;

import com.google.common.collect.ImmutableMap;
import dd.kms.hippodamus.aggregation.Aggregator;

import java.util.concurrent.ExecutorService;

public class Coordinators
{
	public static ExecutionCoordinator createExecutionCoordinator() {
		return new ExecutionCoordinatorImpl();
	}

	public static <S, T> AggregationCoordinator<S, T> createAggregationCoordinator(Aggregator<S, T> aggregator) {
		return new AggregationCoordinatorImpl<>(aggregator);
	}

	// TODO: Remove this method; only used for current tests
	public static <S, T> AggregationCoordinator<S, T> createAggregationCoordinator(Aggregator<S, T> aggregator, ExecutorService executorService) {
		ExecutorServiceWrapper executorServiceWrapper = new ExecutorServiceWrapper(executorService, true);
		ImmutableMap<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType = ImmutableMap.<Integer, ExecutorServiceWrapper>builder()
			.put(TaskType.REGULAR, executorServiceWrapper)
			.put(TaskType.IO, executorServiceWrapper)
			.build();
		return new AggregationCoordinatorImpl<>(aggregator, executorServiceWrappersByTaskType);
	}
}
