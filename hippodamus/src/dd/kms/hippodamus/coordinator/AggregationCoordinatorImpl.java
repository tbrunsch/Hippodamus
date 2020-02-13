package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.coordinator.configuration.CoordinatorConfiguration;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalSupplier;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.execution.configuration.AggregationConfigurationBuilderImpl;
import dd.kms.hippodamus.execution.configuration.ExecutionConfiguration;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.ArrayList;
import java.util.List;

public class AggregationCoordinatorImpl<S, T>
	extends ExecutionCoordinatorImpl
	implements AggregationCoordinator<S, T>
{
	private final Aggregator<S, T>		aggregator;
	private final List<ResultHandle<S>>	aggregatedHandles			= new ArrayList<>();
	private volatile boolean			aggregationCompletedEarlier;

	public AggregationCoordinatorImpl(Aggregator<S, T> aggregator, CoordinatorConfiguration coordinatorConfiguration) {
		super(coordinatorConfiguration);
		this.aggregator = aggregator;
	}

	public <E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable, ExecutionConfiguration configuration) throws E {
		synchronized (this) {
			boolean initiallyStopped = aggregator.hasAggregationCompleted();
			ResultHandle<S> handle = execute(callable, configuration, initiallyStopped);
			if (!initiallyStopped) {
				handle.onCompletion(() -> aggregate(handle.get()));
			}
			aggregatedHandles.add(handle);
			return handle;
		}
	}

	private void aggregate(S value) {
		synchronized (this) {
			aggregator.aggregate(value);
			if (aggregator.hasAggregationCompleted()) {
				aggregationCompletedEarlier = true;
				stop();
			}
		}
	}

	@Override
	public void close() {
		super.close();
		if (aggregationCompletedEarlier) {
			return;
		}
		synchronized (this) {
			for (Handle handle : aggregatedHandles) {
				if (handle.hasCompleted()) {
					continue;
				}
				if (!handle.hasStopped()) {
					log(LogLevel.INTERNAL_ERROR, handle, "Coordinator closed although task has neither completed nor stopped");
				}
			}
		}
	}

	/**
	 * The following methods are only syntactic sugar for simplifying the calls and
	 * delegate to a real builder.
	 */
	@Override
	public <E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable) throws E {
		return configure().aggregate(callable);
	}

	@Override
	public <E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable) throws E {
		return configure().aggregate(callable);
	}

	@Override
	public AggregationConfigurationBuilder<S, T> configure() {
		return new AggregationConfigurationBuilderImpl<>(this);
	}
}
