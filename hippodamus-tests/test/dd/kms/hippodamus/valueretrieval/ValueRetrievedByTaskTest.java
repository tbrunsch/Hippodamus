package dd.kms.hippodamus.valueretrieval;

import com.google.common.base.Preconditions;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.ValueReference;
import dd.kms.hippodamus.testUtils.coordinator.TestExecutionCoordinator;
import dd.kms.hippodamus.testUtils.events.CoordinatorEvent;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

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
class ValueRetrievedByTaskTest extends AbstractValueRetrievedTest
{
	@ParameterizedTest(name = "start/end state when retrieving value: {0}/{1}")
	@MethodSource("getParameters")
	void testValueRetrieval(ValueRetrievalTaskState retrievalStartState, ValueRetrievalTaskState retrievalEndState) {
		Assumptions.assumeTrue(TestUtils.getDefaultParallelism() >= 2);

		boolean stopSupplier = retrievalEndState == ValueRetrievalTaskState.STOPPED_BEFORE_TERMINATION;
		boolean supplierWithException = retrievalEndState == ValueRetrievalTaskState.TERMINATED_EXCEPTIONALLY;

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
		try (TestExecutionCoordinator coordinator = TestUtils.wrap(coordinatorBuilder.build(), eventManager)) {
			resultTask = coordinator.execute(() -> runResultTask(supplierTaskReference, startValueRetrievalFlag, eventManager));

			dummyTask = coordinator.execute(this::runDummyTask);

			supplierTask = coordinator.configure().dependencies(dummyTask).execute(() -> runSupplierTask(coordinator, stopSupplier, supplierWithException));
			supplierTaskReference.set(supplierTask);

			switch (retrievalStartState) {
				case NOT_YET_EXECUTED:
					startValueRetrievalRunnable.run();
					break;
				case EXECUTING:
					eventManager.onHandleEvent(supplierTask, HandleState.STARTED, startValueRetrievalRunnable);
					break;
				case STOPPED_BEFORE_TERMINATION:
					eventManager.onCoordinatorEvent(CoordinatorState.STOPPED_EXTERNALLY, startValueRetrievalRunnable);
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
		} catch (TestException e) {
			encounteredSupplierException = true;
		} catch (CancellationException e) {
			encounteredCancellationException = true;
		}

		Preconditions.checkState(resultTask != null, "Result task handle is null");
		Preconditions.checkState(dummyTask != null, "Dummy task handle is null");
		Preconditions.checkState(supplierTask != null, "Supplier task handle is null");

		HandleEvent dummyCompletedEvent = new HandleEvent(dummyTask, HandleState.COMPLETED);
		HandleEvent supplierStartedEvent = new HandleEvent(supplierTask, HandleState.STARTED);
		CoordinatorEvent coordinatorStoppedEvent = new CoordinatorEvent(CoordinatorState.STOPPED_EXTERNALLY);
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
		Assertions.assertEquals(supplierWithException, encounteredSupplierException);

		Throwable supplierTaskException = supplierTask.getException();
		Throwable resultTaskException = resultTask.getException();
		Assertions.assertEquals(supplierTaskException, eventManager.getException(supplierTask));
		Assertions.assertEquals(resultTaskException, eventManager.getException(resultTask));

		if (supplierWithException) {
			Assertions.assertTrue(supplierTaskException instanceof TestException);

			Assertions.assertTrue(resultTaskException instanceof CompletionException);
			Assertions.assertTrue(resultTaskException.getCause() == supplierTaskException);

			Assertions.assertTrue(eventManager.getDurationMs(supplierTerminatedExceptionallyEvent, resultTask, HandleState.TERMINATED_EXCEPTIONALLY) <= PRECISION_MS);
		} else {
			Assertions.assertNull(supplierTaskException);

			if (stopSupplier) {
				Assertions.assertTrue(resultTaskException instanceof CancellationException);
				Assertions.assertTrue(eventManager.getException(resultTask) instanceof CancellationException);

				Assertions.assertTrue(eventManager.getDurationMs(coordinatorStoppedEvent, resultTask, HandleState.TERMINATED_EXCEPTIONALLY) <= PRECISION_MS);
			} else {
				Assertions.assertNull(resultTaskException);
				Assertions.assertEquals(SUPPLIER_VALUE, resultTask.get());

				Assertions.assertTrue(eventManager.getDurationMs(supplierCompletedEvent, RetrievalEvent.END) <= PRECISION_MS);
			}
		}
	}

	private int runResultTask(ValueReference<ResultHandle<Integer>> supplierTaskReference, ValueReference<Boolean> resultTaskStartFlag, TestEventManager eventManager) {
		while (!resultTaskStartFlag.get());
		ResultHandle<Integer> supplierTask;
		while ((supplierTask = supplierTaskReference.get()) == null);
		eventManager.fireEvent(RetrievalEvent.START);
		int value = supplierTask.get();
		eventManager.fireEvent(RetrievalEvent.END);
		return value;
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
}
