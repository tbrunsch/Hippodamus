package dd.kms.hippodamus.execution.configuration;

import dd.kms.hippodamus.execution.AggregationManager;

public interface AggregationConfigurationBuilder<S, R> extends
	AggregationManager<S>,
	ExecutionConfigurationBuilder<AggregationConfigurationBuilder<S, R>>
{
}
