package dd.kms.hippodamus.testUtils.coordinator;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.execution.configuration.TestExecutionConfigurationBuilder;

public class TestExecutionCoordinator extends BaseTestCoordinator<ExecutionCoordinator>
{
	TestExecutionCoordinator(ExecutionCoordinator wrappedCoordinator, TestEventManager eventManager) {
		super(wrappedCoordinator, eventManager);
	}

	@Override
	public ExecutionConfigurationBuilder configure() {
		return new TestExecutionConfigurationBuilder(wrappedCoordinator.configure(), this);
	}
}
