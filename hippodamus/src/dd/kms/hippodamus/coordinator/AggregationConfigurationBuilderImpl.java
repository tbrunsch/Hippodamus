package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.handles.ResultHandle;

class AggregationConfigurationBuilderImpl<S, T>
	extends ExecutionConfigurationBuilderImpl<AggregationCoordinatorImpl<S, T>, AggregationConfigurationBuilder<S, T>>
	implements AggregationConfigurationBuilder<S, T>
{
	AggregationConfigurationBuilderImpl(AggregationCoordinatorImpl<S, T> coordinator) {
		super(coordinator);
	}

	@Override
	public <E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable) throws E {
		return coordinator.aggregate(callable, createConfiguration());
	}

	@Override
	public <E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable) throws E {
		return coordinator.aggregate(callable, createConfiguration());
	}
}
