package dd.kms.hippodamus.coordinator;

public interface AggregationConfigurationBuilder<S, T> extends
	AggregationManager<S>,
	ExecutionConfigurationBuilder<AggregationConfigurationBuilder<S, T>>
{
}
