package dd.kms.hippodamus.testUtils.execution.configuration;

import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator;

public class TestExecutionConfigurationBuilder extends BaseTestConfigurationBuilder<ExecutionConfigurationBuilder>
{
	public TestExecutionConfigurationBuilder(ExecutionConfigurationBuilder wrappedBuilder, BaseTestCoordinator<?> testCoordinator) {
		super(wrappedBuilder, testCoordinator);
	}

	@Override
	ExecutionConfigurationBuilder getBuilder() {
		return this;
	}
}
