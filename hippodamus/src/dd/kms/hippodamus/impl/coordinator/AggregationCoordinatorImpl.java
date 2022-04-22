package dd.kms.hippodamus.impl.coordinator;

import dd.kms.hippodamus.api.aggregation.Aggregator;
import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.api.execution.configuration.AggregationConfigurationBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.impl.execution.configuration.AggregationConfigurationBuilderImpl;
import dd.kms.hippodamus.impl.execution.configuration.TaskConfiguration;

import java.util.Map;

public class AggregationCoordinatorImpl<S, R> extends ExecutionCoordinatorImpl implements AggregationCoordinator<S, R>
{
	private final Aggregator<S, R>	aggregator;

	public AggregationCoordinatorImpl(Aggregator<S, R> aggregator, Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies, WaitMode waitMode) {
		super(executorServiceWrappersByTaskType, logger, minimumLogLevel, verifyDependencies, waitMode);
		this.aggregator = aggregator;
	}

	public <T extends Throwable> ResultHandle<S> aggregate(StoppableExceptionalCallable<S, T> callable, TaskConfiguration taskConfiguration) throws T {
		synchronized (this) {
			boolean initiallyStopped = aggregator.hasAggregationCompleted();
			ResultHandle<S> handle = execute(callable, taskConfiguration, initiallyStopped);
			if (!initiallyStopped) {
				handle.onCompletion(() -> aggregate(handle));
			}
			return handle;
		}
	}

	private void aggregate(ResultHandle<S> handle) {
		synchronized (this) {
			S value = handle.get();
			aggregator.aggregate(value);
			if (aggregator.hasAggregationCompleted()) {
				stop();
			}
		}
	}

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
