package dd.kms.hippodamus.valueretrieval;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.TestEvent;
import dd.kms.hippodamus.testUtils.events.TestEvents;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

abstract class AbstractValueRetrievedTest
{
	private static final int	DUMMY_TASK_TIME_MS				= 1000;
	private static final int	SUPPLIER_TASK_SLEEP_TIME_MS		= 100;
	private static final int	SUPPLIER_TASK_SLEEP_REPETITIONS	= 10;

	static final int			PRECISION_MS					= 100;

	static final int			SUPPLIER_VALUE					= 42;

	static final TestEvent		RETRIEVAL_STARTED_EVENT			= TestEvents.create("Value retrieval started");
	static final TestEvent		RETRIEVAL_ENDED_EVENT			= TestEvents.create("Value retrieval ended");
	static final TestEvent		RETRIEVAL_EXCEPTION_EVENT		= TestEvents.create("Value retrieval with exception");

	void runDummyTask() {
		TestUtils.simulateWork(DUMMY_TASK_TIME_MS);
	}

	int runSupplierTask(ExecutionCoordinator coordinator, boolean stop, boolean throwException) throws TestException {
		for (int i = 0; i < SUPPLIER_TASK_SLEEP_REPETITIONS; i++) {
			TestUtils.simulateWork(SUPPLIER_TASK_SLEEP_TIME_MS);
			if (stop) {
				coordinator.stop();
				stop = false;
			} else if (throwException) {
				throw new TestException();
			}
		}
		return SUPPLIER_VALUE;
	}

	static List<ValueRetrievalTaskState> getPossibleRetrievalEndStates() {
		return Arrays.stream(ValueRetrievalTaskState.values()).filter(ValueRetrievalTaskState::isReadyToJoin).collect(Collectors.toList());
	}

	static List<ValueRetrievalTaskState> getPossibleRetrievalEndStates(ValueRetrievalTaskState retrievalStartState) {
		return retrievalStartState.isReadyToJoin()
			? Collections.singletonList(retrievalStartState)
			: getPossibleRetrievalEndStates();
	}
}
