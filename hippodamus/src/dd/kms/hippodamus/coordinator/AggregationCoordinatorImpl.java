package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalSupplier;
import dd.kms.hippodamus.exceptions.Exceptions;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AggregationCoordinatorImpl<S, T>
	extends ExecutionCoordinatorImpl
	implements AggregationCoordinator<S, T>
{
	private final Aggregator<S, T>		aggregator;
	private final List<ResultHandle<S>>	aggregatedHandles			= new ArrayList<>();
	private boolean						aggregationCompletedEarlier;

	AggregationCoordinatorImpl(Aggregator<S, T> aggregator) {
		this.aggregator = aggregator;
	}

	AggregationCoordinatorImpl(Aggregator<S, T> aggregator, Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType) {
		// TODO: Should also be called via a builder
		super(executorServiceWrappersByTaskType);
		this.aggregator = aggregator;
	}

	<E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable, ExecutionConfiguration configuration) throws E {
		return aggregate(Exceptions.asStoppable(callable), configuration);
	}

	<E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable, ExecutionConfiguration configuration) throws E {
		return register(() -> super.execute(callable, configuration));
	}

	private <E extends Exception> ResultHandle<S> register(ExceptionalSupplier<ResultHandle<S>, E> handleSupplier) throws E {
		synchronized (this) {
			if (aggregator.hasAggregationCompleted()) {
				return super.createStoppedHandle();
			}
			final ResultHandle<S> handle;
			handle = handleSupplier.get();
			handle.onCompletion(() -> aggregate(handle.get()));
			aggregatedHandles.add(handle);
			return handle;
		}
	}

	/*
	 * Note: This method will either be called in the thread coordinator's thread or in the thread
	 * that executed the aggregation task.
	 */
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
	public void close() throws InterruptedException {
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
				// TODO: Do we have to handle the following case in the basic close method?
				// handle the case that at least one aggregation task has been stopped manually
				throw new InterruptedException("Not all handles have completed");
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
