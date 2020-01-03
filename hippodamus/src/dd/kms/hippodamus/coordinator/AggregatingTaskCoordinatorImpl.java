package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.common.ReadableValue;
import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalSupplier;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AggregatingTaskCoordinatorImpl<S, T> extends BasicTaskCoordinator implements AggregatingTaskCoordinator<S, T>
{
	private final Aggregator<S, T>		aggregator;
	private final List<ResultHandle<S>>	aggregatedHandles			= new ArrayList<>();
	private boolean						aggregationCompletedEarlier;

	AggregatingTaskCoordinatorImpl(Aggregator<S, T> aggregator) {
		this.aggregator = aggregator;
	}

	AggregatingTaskCoordinatorImpl(Aggregator<S, T> aggregator, Map<Integer, ExecutorServiceWrapper> executorServiceWrappersById) {
		// TODO: Should also be called via a builder
		super(executorServiceWrappersById);
		this.aggregator = aggregator;
	}

	@Override
	public <E extends Exception> ResultHandle<S> aggregate(ExceptionalCallable<S, E> callable, int executorServiceId, Handle... dependencies) throws E {
		return register(() -> super.execute(callable, executorServiceId, dependencies));
	}

	@Override
	public <E extends Exception> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, E> callable, int executorServiceId, Handle... dependencies) throws E {
		return register(() -> super.execute(callable, executorServiceId, dependencies));
	}

	@Override
	public ReadableValue<T> getResultValue() {
		return new AggregationResultValue();
	}

	private <E extends Exception> ResultHandle<S> register(ExceptionalSupplier<ResultHandle<S>, E> handleSupplier) throws E {
		synchronized (this) {
			if (aggregator.hasAggregationCompleted()) {
				return super.createStoppedHandle();
			}
			final ResultHandle<S> handle;
			try {
				handle = handleSupplier.get();
			} catch (InterruptedException e) {
				// TODO: think how to handle this: can it occur? should we pretend it to occur?
				e.printStackTrace();
				throw new IllegalStateException(e);
			}
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

	private class AggregationResultValue implements ReadableValue<T>
	{
		@Override
		public T get() {
			return aggregator.getAggregatedValue();
		}
	}
}
