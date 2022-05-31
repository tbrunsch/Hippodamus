package dd.kms.hippodamus.testUtils.coordinator;

import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.events.TestEventManager;

public class TestCoordinators
{
	public static TestExecutionCoordinator wrap(ExecutionCoordinator coordinator, TestEventManager eventManager) {
		return new TestExecutionCoordinator(coordinator, eventManager);
	}

	public static <S, R> TestAggregationCoordinator<S, R> wrap(AggregationCoordinator<S, R> coordinator, TestEventManager eventManager) {
		return new TestAggregationCoordinator(coordinator, eventManager);
	}
}
