package dd.kms.hippodamus.impl.coordinator;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.impl.coordinator.configuration.CoordinatorConfiguration;
import dd.kms.hippodamus.impl.execution.configuration.AggregationConfigurationBuilderImpl;
import dd.kms.hippodamus.impl.execution.configuration.ExecutionConfiguration;

public class AggregationCoordinatorImpl<S, R>
	extends ExecutionCoordinatorImpl
	implements AggregationCoordinator<S, R>
{
	private final Aggregator<S, R>		aggregator;

	public AggregationCoordinatorImpl(Aggregator<S, R> aggregator, CoordinatorConfiguration coordinatorConfiguration) {
		super(coordinatorConfiguration);
		this.aggregator = aggregator;
	}

	public <T extends Throwable> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, T> callable, ExecutionConfiguration configuration) throws T {
		synchronized (this) {
			boolean initiallyStopped = aggregator.hasAggregationCompleted();
			ResultHandle<S> handle = execute(callable, configuration, initiallyStopped);
			if (!initiallyStopped) {
				handle.onCompletion(() -> aggregate(handle.get()));
			}
			return handle;
		}
	}

	private void aggregate(S value) {
		synchronized (this) {
			aggregator.aggregate(value);
			if (aggregator.hasAggregationCompleted()) {
				stop();
			}
		}
	}

	/**
	 * The following methods are only syntactic sugar for simplifying the calls and
	 * delegate to a real builder.
	 */
	@Override
	public <T extends Throwable> ResultHandle<S> aggregate(ExceptionalCallable<S, T> callable) throws T {
		return configure().aggregate(callable);
	}

	@Override
	public <T extends Throwable> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, T> callable) throws T {
		return configure().aggregate(callable);
	}

	@Override
	public AggregationConfigurationBuilder<S, R> configure() {
		return new AggregationConfigurationBuilderImpl<>(this);
	}
}
