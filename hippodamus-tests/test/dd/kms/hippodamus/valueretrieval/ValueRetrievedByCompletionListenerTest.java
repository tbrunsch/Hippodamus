package dd.kms.hippodamus.valueretrieval;

import com.google.common.base.Preconditions;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.ValueReference;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test checks that retrieving a task's value a completion listener works correctly independent of the
 * task's state. We do this by submitting the following tasks in the specified order:
 * <ol>
 *     <li>
 *         A dummy task that is run before the supplier task. We need this task such that we can submit the supplier
 *         task to the coordinator and such that we can be sure that the supplier task is not submitted to the
 *         underlying {@code ExecutorService} immediately.
 *     </li>
 *     <li>
 *         A supplier task whose value we retrieve from the completion listener.
 *     </li>
 * </ol>
 */
class ValueRetrievedByCompletionListenerTest extends AbstractValueRetrievedTest
{
	@ParameterizedTest(name = "end state of supplier task: {0}")
	@MethodSource("getPossibleRetrievalEndStates")
	void testValueRetrieval(ValueRetrievalTaskState supplierTaskEndState) {
		boolean stopSupplier = supplierTaskEndState == ValueRetrievalTaskState.STOPPED_BEFORE_TERMINATION;
		boolean supplierWithException = supplierTaskEndState == ValueRetrievalTaskState.TERMINATED_EXCEPTIONALLY;

		ValueReference<Integer> resultRetrievedByCompletionListener = new ValueReference<>();
		ValueReference<Throwable> exceptionEncounteredByCompletionListener = new ValueReference<>();
		ValueReference<Throwable> exceptionEncounteredByExceptionListener = new ValueReference<>();

		Handle dummyTask = null;
		ResultHandle<Integer> supplierTask = null;
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.COMPUTATIONAL, 1);
		boolean encounteredSupplierException = false;
		TestEventManager eventManager = new TestEventManager();
		try (ExecutionCoordinator coordinator = TestUtils.wrap(coordinatorBuilder.build(), eventManager)) {
			dummyTask = coordinator.execute(this::runDummyTask);

			supplierTask = coordinator.configure().dependencies(dummyTask).execute(() -> runSupplierTask(coordinator, stopSupplier, supplierWithException));
			final ResultHandle<Integer> finalSupplierTask = supplierTask;

			finalSupplierTask.onCompletion(() -> {
				eventManager.fireEvent(RETRIEVAL_STARTED_EVENT);
				try {
					int value = finalSupplierTask.get();
					eventManager.fireEvent(RETRIEVAL_ENDED_EVENT);
					resultRetrievedByCompletionListener.set(value);

				} catch (Throwable t) {
					exceptionEncounteredByCompletionListener.set(t);
				}
			});
			finalSupplierTask.onException(() -> {
				exceptionEncounteredByExceptionListener.set(finalSupplierTask.getException());
				eventManager.fireEvent(RETRIEVAL_EXCEPTION_EVENT);
			});

		} catch (TestException e) {
			encounteredSupplierException = true;
		}

		Preconditions.checkState(dummyTask != null, "Dummy task handle is null");
		Preconditions.checkState(supplierTask != null, "Supplier task handle is null");

		HandleEvent dummyCompletedEvent = new HandleEvent(dummyTask, HandleState.COMPLETED);
		HandleEvent supplierStartedEvent = new HandleEvent(supplierTask, HandleState.STARTED);
		HandleEvent supplierCompletedEvent = new HandleEvent(supplierTask, HandleState.COMPLETED);

		/*
		 * Test that the test setup is as specified by the parameters
		 */
		Assertions.assertTrue(eventManager.before(dummyCompletedEvent, supplierStartedEvent));

		if (supplierWithException) {
			Assertions.assertFalse(eventManager.encounteredEvent(RETRIEVAL_STARTED_EVENT));

			Assertions.assertTrue(eventManager.getDurationMs(supplierTask, HandleState.TERMINATED_EXCEPTIONALLY, RETRIEVAL_EXCEPTION_EVENT) <= PRECISION_MS);
		} else {
			Assertions.assertTrue(eventManager.before(supplierCompletedEvent, RETRIEVAL_STARTED_EVENT));

			Assertions.assertTrue(eventManager.getDurationMs(supplierCompletedEvent, RETRIEVAL_ENDED_EVENT) <= PRECISION_MS);
		}

		/*
		 * Test that the tasks and the coordinator behave as expected
		 */
		Assertions.assertEquals(supplierWithException, encounteredSupplierException);
		Assertions.assertNull(exceptionEncounteredByCompletionListener.get());

		Throwable supplierTaskException = supplierTask.getException();
		Assertions.assertEquals(supplierTaskException, eventManager.getException(supplierTask));
		Assertions.assertEquals(supplierTaskException, exceptionEncounteredByExceptionListener.get());

		if (supplierWithException) {
			Assertions.assertTrue(supplierTaskException instanceof TestException);
		} else {
			Assertions.assertNull(supplierTaskException);
			Assertions.assertEquals(SUPPLIER_VALUE, resultRetrievedByCompletionListener.get());
		}
	}
}
