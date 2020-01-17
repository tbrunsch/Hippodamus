package dd.kms.hippodamus.execution.configuration;

import dd.kms.hippodamus.execution.AggregationManager;

public interface AggregationConfigurationBuilder<S, T> extends
	AggregationManager<S>,
	ExecutionConfigurationBuilder<AggregationConfigurationBuilder<S, T>>
{
}
