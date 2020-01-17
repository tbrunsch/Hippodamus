package dd.kms.hippodamus.execution.configuration;

import dd.kms.hippodamus.coordinator.AggregationCoordinatorImpl;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.Exceptions;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.handles.ResultHandle;

public class AggregationConfigurationBuilderImpl<S, T>
	extends ExecutionConfigurationBuilderImpl<AggregationCoordinatorImpl<S, T>, AggregationConfigurationBuilder<S, T>>
	implements AggregationConfigurationBuilder<S, T>
{
	public AggregationConfigurationBuilderImpl(AggregationCoordinatorImpl<S, T> coordinator) {
		super(coordinator);
	}

	@Override
	public <E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable) throws E {
		return aggregate(Exceptions.asStoppable(callable));
	}

	@Override
	public <E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable) throws E {
		return coordinator.aggregate(callable, createConfiguration());
	}
}
