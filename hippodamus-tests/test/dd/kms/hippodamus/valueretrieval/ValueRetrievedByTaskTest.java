package dd.kms.hippodamus.valueretrieval;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.ValueReference;
import dd.kms.hippodamus.testUtils.coordinator.TestCoordinators;
import dd.kms.hippodamus.testUtils.coordinator.TestExecutionCoordinator;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * This test checks that a task correctly retrieves a value from another task independent of the state of the
 * other task. We do this by submitting the following tasks in the specified order:
 * <ol>
 *     <li>A retrieval task that tries to retrieve the value of the supplier task</li>
 *     <li>
 *         A dummy task that is run before the supplier task. We need this task such that we can submit
 *         the supplier task to the coordinator (because we have to hand its handle over to the retrieval task)
 *         and such that we can be sure that the supplier task is not submitted to the underlying {@code ExecutorService}
 *         immediately.
 *     </li>
 *     <li>
 *         A supplier task whose value is retrieved by the retrieval task.
 *     </li>
 * </ol>
 */
public class ValueRetrievedByTaskTest
{
	private static final int	DUMMY_TASK_TIME_MS				= 1000;
	private static final int	SUPPLIER_TASK_SLEEP_TIME_MS		= 100;
	private static final int	SUPPLIER_TASK_SLEEP_REPETITIONS	= 10;

	private static final int	SUPPLIER_VALUE		= 42;

	@ParameterizedTest(name = "start/end state when retrieving value: {0}/{1}")
	@MethodSource("getParameters")
	void testValueRetrieval(ValueRetrievalTaskState retrievalStartState, ValueRetrievalTaskState retrievalEndState) {
		Assumptions.assumeTrue(TestUtils.getDefaultParallelism() >= 2);

		// reference to supplier task; will be set when supplier task has been submitted to the coordinator
		ValueReference<ResultHandle<Integer>> supplierTaskReference = new ValueReference<>();

		// tells the retrieval task when to start retrieving the value of the supplier task
		ValueReference<Boolean> startValueRetrievalFlag = new ValueReference<>(false);
		Runnable startValueRetrievalRunnable = () -> startValueRetrievalFlag.set(true);

		ResultHandle<Integer> resultTask = null;
		Handle dummyTask = null;
		ResultHandle<Integer> supplierTask = null;
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.COMPUTATIONAL, 2);
		boolean encounteredSupplierException = false;
		boolean encounteredCancellationException = false;
		TestEventManager eventManager = new TestEventManager();
		try (TestExecutionCoordinator coordinator = TestCoordinators.wrap(coordinatorBuilder.build(), eventManager)) {
			resultTask = coordinator.execute(() -> runResultTask(supplierTaskReference, startValueRetrievalFlag, eventManager));

			dummyTask = coordinator.execute(() -> runDummyTask());

			supplierTask = coordinator.configure().dependencies(dummyTask).execute(() -> supplierTask(coordinator, retrievalEndState));
			supplierTaskReference.set(supplierTask);

			switch (retrievalStartState) {
				case NOT_YET_EXECUTED:
					startValueRetrievalRunnable.run();
					break;
				case EXECUTING:
					eventManager.onHandleEvent(supplierTask, HandleState.STARTED, startValueRetrievalRunnable);
					break;
				case STOPPED_BEFORE_TERMINATION:
					eventManager.onHandleEvent(supplierTask, HandleState.STOPPED, startValueRetrievalRunnable);
					break;
				case TERMINATED_REGULARLY:
					supplierTask.onCompletion(startValueRetrievalRunnable);
					break;
				case TERMINATED_EXCEPTIONALLY:
					supplierTask.onException(startValueRetrievalRunnable);
					break;
				default:
					throw new UnsupportedOperationException("Unsupported retrieval task state: " + retrievalStartState);
			}
		} catch (SupplierException e) {
			encounteredSupplierException = true;
		} catch (CancellationException e) {
			encounteredCancellationException = true;
		}

		if (resultTask == null) {
			throw new IllegalStateException("Result task handle is null");
		}
		if (dummyTask == null) {
			throw new IllegalStateException("Dummy task handle is null");
		}
		if (supplierTask == null) {
			throw new IllegalStateException("Supplier task handle is null");
		}

		Assertions.assertTrue(eventManager.before(dummyTask, HandleState.COMPLETED, supplierTask, HandleState.STARTED));

		RetrievalStartedEvent retrievalEvent = new RetrievalStartedEvent();
		HandleEvent supplierStartedEvent = new HandleEvent(supplierTask, HandleState.STARTED, null);
		HandleEvent supplierStoppedEvent = new HandleEvent(supplierTask, HandleState.STOPPED, null);
		HandleEvent supplierCompletedEvent = new HandleEvent(supplierTask, HandleState.COMPLETED, null);
		HandleEvent supplierTerminatedExceptionallyEvent = new HandleEvent(supplierTask, HandleState.TERMINATED_EXCEPTIONALLY, null);

		switch (retrievalStartState) {
			case NOT_YET_EXECUTED:
				Assertions.assertTrue(eventManager.before(retrievalEvent, supplierStartedEvent));
				break;
			case EXECUTING:
				Assertions.assertTrue(eventManager.before(supplierStartedEvent, retrievalEvent));
				break;
			case STOPPED_BEFORE_TERMINATION:
				Assertions.assertTrue(eventManager.before(supplierStoppedEvent, retrievalEvent));
				break;
			case TERMINATED_REGULARLY:
				Assertions.assertTrue(eventManager.before(supplierCompletedEvent, retrievalEvent));
				break;
			case TERMINATED_EXCEPTIONALLY:
				Assertions.assertTrue(eventManager.before(supplierTerminatedExceptionallyEvent, retrievalEvent));
				break;
		}

		boolean stopped = retrievalEndState == ValueRetrievalTaskState.STOPPED_BEFORE_TERMINATION;
		boolean exceptionThrown = retrievalEndState == ValueRetrievalTaskState.TERMINATED_EXCEPTIONALLY;

		Assertions.assertEquals(stopped, encounteredCancellationException);
		Assertions.assertEquals(exceptionThrown, encounteredSupplierException);

		Throwable resultTaskException = resultTask.getException();
		if (stopped) {
			Assertions.assertTrue(resultTaskException instanceof CancellationException);
		} else if (exceptionThrown) {
			Assertions.assertTrue(resultTaskException instanceof CompletionException);
			Assertions.assertTrue(resultTaskException.getCause() instanceof SupplierException);
		} else {
			Assertions.assertNull(resultTaskException);
			Assertions.assertEquals(SUPPLIER_VALUE, resultTask.get());
		}
	}

	private int runResultTask(ValueReference<ResultHandle<Integer>> supplierTaskReference, ValueReference<Boolean> resultTaskStartFlag, TestEventManager eventManager) {
		while (!resultTaskStartFlag.get());
		ResultHandle<Integer> supplierTask;
		while ((supplierTask = supplierTaskReference.get()) == null);
		eventManager.encounteredEvent(new RetrievalStartedEvent());
		return supplierTask.get();
	}

	private void runDummyTask() {
		TestUtils.simulateWork(DUMMY_TASK_TIME_MS);
	}

	private int supplierTask(ExecutionCoordinator coordinator, ValueRetrievalTaskState retrievalEndState) throws SupplierException {
		for (int i = 0; i < SUPPLIER_TASK_SLEEP_REPETITIONS; i++) {
			TestUtils.simulateWork(SUPPLIER_TASK_SLEEP_TIME_MS);
			switch (retrievalEndState) {
				case NOT_YET_EXECUTED:
				case EXECUTING:
					throw new IllegalStateException("Invalid end state for value retrieval");
				case STOPPED_BEFORE_TERMINATION:
					coordinator.stop();
					break;
				case TERMINATED_REGULARLY:
					break;
				case TERMINATED_EXCEPTIONALLY:
					throw new SupplierException();
				default:
					throw new UnsupportedOperationException("Unsupported retrieval task state: " + retrievalEndState);
			}
		}
		return SUPPLIER_VALUE;
	}

	static List<Object[]> getParameters() {
		List<Object[]> retrievalStartEndStatePairs = new ArrayList<>();
		for (ValueRetrievalTaskState retrievalStartState : ValueRetrievalTaskState.values()) {
			List<ValueRetrievalTaskState> retrievalEndStates = getPossibleRetrievalEndStates(retrievalStartState);
			for (ValueRetrievalTaskState retrievalEndState : retrievalEndStates) {
				retrievalStartEndStatePairs.add(new Object[]{retrievalStartState, retrievalEndState});
			}
		}
		return retrievalStartEndStatePairs;
	}

	private static List<ValueRetrievalTaskState> getPossibleRetrievalEndStates(ValueRetrievalTaskState retrievalStartState) {
		return retrievalStartState.isReadyToJoin()
			? Collections.singletonList(retrievalStartState)
			: Arrays.stream(ValueRetrievalTaskState.values()).filter(state -> state.isReadyToJoin()).collect(Collectors.toList());
	}

	private static class RetrievalStartedEvent extends TestEvent
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
	}

	private static class SupplierException extends Exception {}
}
