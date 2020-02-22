package dd.kms.hippodamus.execution.configuration;

import dd.kms.hippodamus.coordinator.AggregationCoordinatorImpl;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.Exceptions;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.handles.ResultHandle;

public class AggregationConfigurationBuilderImpl<S, R>
	extends ExecutionConfigurationBuilderImpl<AggregationCoordinatorImpl<S, R>, AggregationConfigurationBuilder<S, R>>
	implements AggregationConfigurationBuilder<S, R>
{
	public AggregationConfigurationBuilderImpl(AggregationCoordinatorImpl<S, R> coordinator) {
		super(coordinator);
	}

	@Override
	public <T extends Throwable> ResultHandle<S> aggregate(ExceptionalCallable<S, T> callable) throws T {
		return aggregate(Exceptions.asStoppable(callable));
	}

	@Override
	public <T extends Throwable> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, T> callable) throws T {
		return coordinator.aggregate(callable, createConfiguration());
	}
}
