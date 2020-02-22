package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.coordinator.configuration.CoordinatorConfiguration;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.execution.configuration.AggregationConfigurationBuilderImpl;
import dd.kms.hippodamus.execution.configuration.ExecutionConfiguration;
import dd.kms.hippodamus.handles.ResultHandle;

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
