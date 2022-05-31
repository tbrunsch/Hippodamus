package dd.kms.hippodamus.valueretrieval;

import com.google.common.base.Preconditions;
import dd.kms.hippodamus.api.coordinator.Coordinators;
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
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * This test checks that retrieving a task's value from the coordinator's thread works correctly independent of the
 * task's state. We do this by submitting the following tasks in the specified order:
 * <ol>
 *     <li>
 *         A dummy task that is run before the supplier task. We need this task such that we can submit the supplier
 *         task to the coordinator and such that we can be sure that the supplier task is not submitted to the
 *         underlying {@code ExecutorService} immediately.
 *     </li>
 *     <li>
 *         A supplier task whose value we retrieve from the coordinator's thread.
 *     </li>
 * </ol>
 */
class ValueRetrievedByCoordinatorThreadTest extends AbstractValueRetrievedTest
{
	@ParameterizedTest(name = "start/end state when retrieving value: {0}/{1}, check exception before value retrieval: {2}")
	@MethodSource("getParameters")
	void testValueRetrieval(ValueRetrievalTaskState retrievalStartState, ValueRetrievalTaskState retrievalEndState, boolean checkExceptionBeforeValueRetrieval) {
		boolean stopSupplier = retrievalEndState == ValueRetrievalTaskState.STOPPED_BEFORE_TERMINATION;
		boolean supplierWithException = retrievalEndState == ValueRetrievalTaskState.TERMINATED_EXCEPTIONALLY;

		// set to true when we may start retrieving the value of the supplier task
		ValueReference<Boolean> startValueRetrievalFlag = new ValueReference<>(false);
		Runnable startValueRetrievalRunnable = () -> startValueRetrievalFlag.set(true);

		Handle dummyTask = null;
		ResultHandle<Integer> supplierTask = null;
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.COMPUTATIONAL, 1);
		boolean encounteredSupplierException = false;
		boolean encounteredCancellationException = false;
		boolean encounteredCompletionException = false;
		TestEventManager eventManager = new TestEventManager();
		int result = 0;
		try (TestExecutionCoordinator coordinator = TestCoordinators.wrap(coordinatorBuilder.build(), eventManager)) {
			dummyTask = coordinator.execute(this::runDummyTask);

			supplierTask = coordinator.configure().dependencies(dummyTask).execute(() -> runSupplierTask(coordinator, stopSupplier, supplierWithException));

			switch (retrievalStartState) {
				case NOT_YET_EXECUTED:
					startValueRetrievalRunnable.run();
					break;
				case EXECUTING:
					eventManager.onHandleEvent(supplierTask, HandleState.STARTED, startValueRetrievalRunnable);
					break;
				case STOPPED_BEFORE_TERMINATION:
					eventManager.onCoordinatorEvent(CoordinatorState.STOPPED, startValueRetrievalRunnable);
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

			while (!startValueRetrievalFlag.get());
			eventManager.fireEvent(RetrievalEvent.START);
			if (checkExceptionBeforeValueRetrieval) {
				coordinator.checkException();
			}
			boolean encounteredException = true;
			try {
				result = supplierTask.get();
				encounteredException = false;
				eventManager.fireEvent(RetrievalEvent.END);
			} finally {
				if (encounteredException) {
					eventManager.fireEvent(RetrievalEvent.EXCEPTION);
				}
			}
		} catch (SupplierException e) {
			encounteredSupplierException = true;
		} catch (CancellationException e) {
			encounteredCancellationException = true;
		} catch (CompletionException e) {
			encounteredCompletionException = true;
		}

		Preconditions.checkState(dummyTask != null, "Dummy task handle is null");
		Preconditions.checkState(supplierTask != null, "Supplier task handle is null");

		HandleEvent dummyCompletedEvent = new HandleEvent(dummyTask, HandleState.COMPLETED);
		HandleEvent supplierStartedEvent = new HandleEvent(supplierTask, HandleState.STARTED);
		CoordinatorEvent coordinatorStoppedEvent = new CoordinatorEvent(CoordinatorState.STOPPED);
		HandleEvent supplierCompletedEvent = new HandleEvent(supplierTask, HandleState.COMPLETED);
		HandleEvent supplierTerminatedExceptionallyEvent = new HandleEvent(supplierTask, HandleState.TERMINATED_EXCEPTIONALLY);

		/*
		 * Test that the test setup is as specified by the parameters
		 */
		Assertions.assertTrue(eventManager.before(dummyCompletedEvent, supplierStartedEvent));

		switch (retrievalStartState) {
			case NOT_YET_EXECUTED:
				Assertions.assertTrue(eventManager.before(RetrievalEvent.START, supplierStartedEvent));
				break;
			case EXECUTING:
				Assertions.assertTrue(eventManager.before(supplierStartedEvent, RetrievalEvent.START));
				break;
			case STOPPED_BEFORE_TERMINATION:
				Assertions.assertTrue(eventManager.before(coordinatorStoppedEvent, RetrievalEvent.START));
				break;
			case TERMINATED_REGULARLY:
				Assertions.assertTrue(eventManager.before(supplierCompletedEvent, RetrievalEvent.START));
				break;
			case TERMINATED_EXCEPTIONALLY:
				Assertions.assertTrue(eventManager.before(supplierTerminatedExceptionallyEvent, RetrievalEvent.START));
				break;
		}

		/*
		 * Test that the tasks and the coordinator behave as expected
		 */
		Assertions.assertEquals(stopSupplier, encounteredCancellationException);
		boolean checkExceptionThrows = checkExceptionBeforeValueRetrieval && retrievalStartState == ValueRetrievalTaskState.TERMINATED_EXCEPTIONALLY;
		if (checkExceptionThrows) {
			/*
			 * Checking the coordinator's exception via ExecutionCoordinator.checkException() before retrieving the
			 * supplier task's value lets the coordinator throw the already encountered SupplierException in this case.
			 */
			Assertions.assertTrue(encounteredSupplierException);
		} else {
			Assertions.assertEquals(supplierWithException, encounteredCompletionException);
		}

		Throwable supplierTaskException = supplierTask.getException();
		Assertions.assertEquals(supplierTaskException, eventManager.getException(supplierTask));

		if (supplierWithException) {
			Assertions.assertTrue(supplierTaskException instanceof SupplierException);

			if (checkExceptionThrows) {
				Assertions.assertTrue(eventManager.getDurationMs(supplierTerminatedExceptionallyEvent, new CoordinatorEvent(CoordinatorState.CLOSED)) <= PRECISION_MS);
			} else {
				Assertions.assertTrue(eventManager.getDurationMs(supplierTerminatedExceptionallyEvent, RetrievalEvent.EXCEPTION) <= PRECISION_MS);
			}
		} else {
			Assertions.assertNull(supplierTaskException);

			if (stopSupplier) {
				Assertions.assertTrue(eventManager.getDurationMs(coordinatorStoppedEvent, RetrievalEvent.EXCEPTION) <= PRECISION_MS);
			} else {
				Assertions.assertEquals(SUPPLIER_VALUE, result);

				Assertions.assertTrue(eventManager.getDurationMs(supplierCompletedEvent, RetrievalEvent.END) <= PRECISION_MS);
			}
		}
	}

	static List<Object[]> getParameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (ValueRetrievalTaskState retrievalStartState : ValueRetrievalTaskState.values()) {
			List<ValueRetrievalTaskState> retrievalEndStates = getPossibleRetrievalEndStates(retrievalStartState);
			for (ValueRetrievalTaskState retrievalEndState : retrievalEndStates) {
				for (boolean checkExceptionBeforeValueRetrieval : TestUtils.BOOLEANS) {
					parameters.add(new Object[]{retrievalStartState, retrievalEndState, checkExceptionBeforeValueRetrieval});
				}
			}
		}
		return parameters;
	}
}
