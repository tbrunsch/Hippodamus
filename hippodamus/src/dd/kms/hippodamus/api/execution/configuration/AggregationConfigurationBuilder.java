package dd.kms.hippodamus.api.execution.configuration;

import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.execution.AggregationManager;
import dd.kms.hippodamus.api.execution.ExecutionController;
import dd.kms.hippodamus.api.handles.Handle;

import java.util.Collection;

/**
 * Same as {@link ExecutionConfigurationBuilder}, but for tasks of an
 * {@link dd.kms.hippodamus.api.coordinator.AggregationCoordinator}.
 * Call {@link dd.kms.hippodamus.api.coordinator.AggregationCoordinator#configure()}
 * to create this builder for a task.
 */
public interface AggregationConfigurationBuilder<S, R> extends ExecutionConfigurationBuilder, AggregationManager<S>
{
	@Override
	AggregationConfigurationBuilder<S, R> name(String name);

	@Override
	AggregationConfigurationBuilder<S, R> taskType(TaskType type);

	@Override
	AggregationConfigurationBuilder<S, R> dependencies(Handle... dependencies);

	@Override
	AggregationConfigurationBuilder<S, R> dependencies(Collection<? extends Handle> dependencies);

	@Override
	AggregationConfigurationBuilder<S, R> executionController(ExecutionController controller);
}
