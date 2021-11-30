package dd.kms.hippodamus.impl.execution.configuration;

import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;

public class ExecutionConfigurationBuilderImpl extends ConfigurationBuilderBaseImpl<ExecutionCoordinatorImpl, ExecutionConfigurationBuilder>
{
	public ExecutionConfigurationBuilderImpl(ExecutionCoordinatorImpl coordinator) {
		super(coordinator);
	}

	@Override
	ExecutionConfigurationBuilder getBuilder() {
		return this;
	}
}
