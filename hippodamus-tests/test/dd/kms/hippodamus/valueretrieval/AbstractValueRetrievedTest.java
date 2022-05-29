package dd.kms.hippodamus.valueretrieval;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.TestEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

abstract class AbstractValueRetrievedTest
{
	private static final int	DUMMY_TASK_TIME_MS				= 1000;
	private static final int	SUPPLIER_TASK_SLEEP_TIME_MS		= 100;
	private static final int	SUPPLIER_TASK_SLEEP_REPETITIONS	= 10;

	static final int			SUPPLIER_VALUE					= 42;

	void runDummyTask() {
		TestUtils.simulateWork(DUMMY_TASK_TIME_MS);
	}

	int runSupplierTask(ExecutionCoordinator coordinator, boolean stop, boolean throwException) throws SupplierException {
		for (int i = 0; i < SUPPLIER_TASK_SLEEP_REPETITIONS; i++) {
			TestUtils.simulateWork(SUPPLIER_TASK_SLEEP_TIME_MS);
			if (stop) {
				coordinator.stop();
				stop = false;
			} else if (throwException) {
				throw new SupplierException();
			}
		}
		return SUPPLIER_VALUE;
	}

	static List<ValueRetrievalTaskState> getPossibleRetrievalEndStates(ValueRetrievalTaskState retrievalStartState) {
		return retrievalStartState.isReadyToJoin()
			? Collections.singletonList(retrievalStartState)
			: Arrays.stream(ValueRetrievalTaskState.values()).filter(ValueRetrievalTaskState::isReadyToJoin).collect(Collectors.toList());
	}

	static class RetrievalStartedEvent extends TestEvent
	{
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			return true;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public String toString() {
			return "Value retrieval started";
		}
	}

	static class SupplierException extends Exception {}
}
