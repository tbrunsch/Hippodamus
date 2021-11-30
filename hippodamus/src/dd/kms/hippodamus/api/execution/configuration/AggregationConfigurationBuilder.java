package dd.kms.hippodamus.api.execution.configuration;

import dd.kms.hippodamus.api.execution.AggregationManager;
import dd.kms.hippodamus.api.handles.Handle;

import java.util.Collection;

public interface AggregationConfigurationBuilder<S, R> extends ExecutionConfigurationBuilder, AggregationManager<S>
{
	@Override
	AggregationConfigurationBuilder<S, R> name(String name);

	@Override
	AggregationConfigurationBuilder<S, R> taskType(int type);

	@Override
	AggregationConfigurationBuilder<S, R> dependencies(Handle... dependencies);

	@Override
	AggregationConfigurationBuilder<S, R> dependencies(Collection<Handle> dependencies);
}
