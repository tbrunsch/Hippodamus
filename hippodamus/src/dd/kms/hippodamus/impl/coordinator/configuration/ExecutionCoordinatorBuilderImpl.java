package dd.kms.hippodamus.impl.coordinator.configuration;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.impl.coordinator.ExecutionCoordinatorImpl;

public class ExecutionCoordinatorBuilderImpl extends CoordinatorBuilderBaseImpl<ExecutionCoordinatorBuilder> implements ExecutionCoordinatorBuilder
{
	@Override
	ExecutionCoordinatorBuilder getBuilder() {
		return this;
	}

	@Override
	public ExecutionCoordinator build() {
		CoordinatorConfiguration configuration = createConfiguration();
		return new ExecutionCoordinatorImpl(configuration);
	}
}
