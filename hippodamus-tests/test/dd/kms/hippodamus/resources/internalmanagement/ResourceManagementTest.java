package dd.kms.hippodamus.resources.internalmanagement;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.resources.ResourceRequestor;
import dd.kms.hippodamus.testUtils.TestException;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;

/**
 * This test checks that Hippodamus correctly updates the 
 */
public class ResourceManagementTest
{
	private static final int	NUM_TASKS					= 10;
	private static final long	TASK_TIME_MS				= 500;
	private static final long	TASK_SUBMISSION_DELAY_MS	= 200;

	private static final int	RESOURCE_CAPACITY			= 2*NUM_TASKS;
	private static final long	PRECISION_MS				= 100;

	@ParameterizedTest(name = "behavior coordinator 1/coordinator 2: {0}/{1}")
	@MethodSource("getCoordinatorBehaviors")
	void testResourceManagement(Behavior coordinatorBehavior1, Behavior coordinatorBehavior2) {
		TestEventManager eventManager = new TestEventManager();
		TestResource resource = new TestResource(RESOURCE_CAPACITY, eventManager);
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int threadIndex = 0; threadIndex < 2; threadIndex++) {
				List<TaskDescription> taskDescriptions = createTaskDescriptions(threadIndex);
				Behavior coordinatorBehavior = threadIndex == 0 ? coordinatorBehavior1 : coordinatorBehavior2;
				coordinator.execute(() -> executeTasks(taskDescriptions, coordinatorBehavior, resource));
			}
		}
	}

	static List<Behavior[]> getCoordinatorBehaviors() {
		List<Behavior[]> coordinatorBehaviors = new ArrayList<>();
		for (Behavior behavior1 : Behavior.values()) {
			for (Behavior behavior2 : Behavior.values()) {
				if (behavior1.compareTo(behavior2) <= 0) {
					coordinatorBehaviors.add(new Behavior[]{behavior1, behavior2});
				}
			}
		}
		return coordinatorBehaviors;
	}

	private static List<TaskDescription> createTaskDescriptions(int threadIndex) {
		List<TaskDescription> taskDescriptions = new ArrayList<>(NUM_TASKS);
		for (int i = 0; i < NUM_TASKS; i++) {
			String name = "Task " + (threadIndex+1) + "." + (i+1);
			TaskDescription taskDescription = new TaskDescription(name, getTaskSize(i, threadIndex), threadIndex);
			taskDescriptions.add(taskDescription);
		}
		return taskDescriptions;
	}

	private static int getTaskSize(int taskIndex, int threadIndex) {
		// arbitrary function such that tasks of both threads have same size, but in different order
		return threadIndex == 0 ? taskIndex : NUM_TASKS - 1 - threadIndex;
	}

	private static void executeTasks(List<TaskDescription> taskDescriptions, Behavior coordinatorBehavior, TestResource resource) {
		int threadIndex = getThreadIndex(taskDescriptions);
		int numTaskDescriptions = taskDescriptions.size();
		int specialTaskIndex = (int) (0.9*(NUM_TASKS-1));
		Map<Handle, TaskDescription> taskDescriptionByHandle = new HashMap<>();
		TestEventManager eventManager = resource.getEventManager();
		try (ExecutionCoordinator coordinator = TestUtils.wrap(Coordinators.createExecutionCoordinator(), eventManager)) {
			for (int i = 0; i < numTaskDescriptions; i++) {
				TaskDescription taskDescription = taskDescriptions.get(i);
				Behavior taskBehavior = i == specialTaskIndex ? coordinatorBehavior : Behavior.TERMINATE_REGULARLY;
				Handle handle = coordinator.configure()
					.name(taskDescription.getName())
					.requiredResource(resource, () -> taskDescription)
					.execute(() -> runTask(coordinator, taskBehavior, eventManager, threadIndex));
				taskDescriptionByHandle.put(handle, taskDescription);
				TestUtils.simulateWork(TASK_SUBMISSION_DELAY_MS);
			}
		} catch (TestException e) {
			Assertions.assertEquals(Behavior.TERMINATE_EXCEPTIONALLY, coordinatorBehavior);
		}
		checkResourceState(resource, taskDescriptionByHandle);
	}

	private static void runTask(ExecutionCoordinator coordinator, Behavior taskBehavior, TestEventManager eventManager, int threadIndex) throws TestException {
		TestUtils.simulateWork(TASK_TIME_MS);
		if (taskBehavior == Behavior.STOP_MANUALLY) {
			coordinator.stop();
		} else if (taskBehavior == Behavior.TERMINATE_EXCEPTIONALLY) {
			throw new TestException();
		}
	}

	private static void checkResourceState(TestResource resource, Map<Handle, TaskDescription> taskDescriptionByHandle) {
		resource = resource.clone();
		checkClearedResource(resource, taskDescriptionByHandle);
		checkResourceEvents(resource.getEventManager(), taskDescriptionByHandle);
	}

	private static void checkClearedResource(TestResource resource, Map<Handle, TaskDescription> taskDescriptionByHandle) {
		Collection<TaskDescription> threadHandles = taskDescriptionByHandle.values();
		int threadIndex = getThreadIndex(threadHandles);

		long totalPendingResourceShares = resource.getTotalPendingResourceShares(threadIndex);
		Assertions.assertEquals(0, totalPendingResourceShares, "Unexpected pending resource shares");

		long acquiredResourceShares = resource.getAcquiredResourceShares(threadIndex);
		Assertions.assertEquals(0, acquiredResourceShares, "Unexpected acquired resource shares");

		List<ResourceRequest> postponedResourceRequests = resource.getPostponedResourceRequests();
		for (ResourceRequest postponedResourceRequest : postponedResourceRequests) {
			ResourceRequestor resourceRequestor = postponedResourceRequest.getResourceRequestor();
			Handle handle = resourceRequestor.getHandle();
			Assertions.assertFalse(threadHandles.contains(handle), "Postponed resource request of '" + handle.getTaskName() + "' has not been removed");
		}
	}

	private static void checkResourceEvents(TestEventManager eventManager, Map<Handle, TaskDescription> taskDescriptionByHandle) {
		checkStartedTaskEvents(eventManager, taskDescriptionByHandle);
		checkTerminatedTaskEvents(eventManager, taskDescriptionByHandle);
		checkPendingResourceEvents(eventManager, taskDescriptionByHandle.values());
		checkResourceEvents(eventManager, taskDescriptionByHandle.values());
	}

	/**
	 * When a task is started, then the total pending resource share size must be decreased and the total acquired
	 * resource share size must be increased.
	 */
	private static void checkStartedTaskEvents(TestEventManager eventManager, Map<Handle, TaskDescription> taskDescriptionByHandle) {
		for (Handle handle : taskDescriptionByHandle.keySet()) {
			HandleEvent startedEvent = new HandleEvent(handle, HandleState.STARTED);
			if (!eventManager.encounteredEvent(startedEvent)) {
				continue;
			}
			long startedTimeMs = eventManager.getElapsedTimeMs(startedEvent);
			TaskDescription taskDescription = taskDescriptionByHandle.get(handle);
			PendingResourceEvent removedPendingResourceEvent = new PendingResourceEvent(taskDescription, PendingResourceEvent.State.REMOVED);
			ResourceEvent acquiredResourceEvent = new ResourceEvent(taskDescription, ResourceEvent.State.ACQUIRED);
			checkEventOccurred(eventManager, removedPendingResourceEvent, startedTimeMs);
			checkEventOccurred(eventManager, acquiredResourceEvent, startedTimeMs);
		}
	}

	/**
	 * When a task terminates, then it must release its resource share.
	 */
	private static void checkTerminatedTaskEvents(TestEventManager eventManager, Map<Handle, TaskDescription> taskDescriptionByHandle) {
		for (Handle handle : taskDescriptionByHandle.keySet()) {
			HandleEvent terminatedEvent = new HandleEvent(handle, HandleState.TERMINATED);
			if (!eventManager.encounteredEvent(terminatedEvent)) {
				continue;
			}
			long terminatedTimeMs = eventManager.getElapsedTimeMs(terminatedEvent);
			TaskDescription taskDescription = taskDescriptionByHandle.get(handle);
			ResourceEvent releaseResourceEvent = new ResourceEvent(taskDescription, ResourceEvent.State.RELEASED);
			checkEventOccurred(eventManager, releaseResourceEvent, terminatedTimeMs);
		}
	}

	private static void checkEventOccurred(TestEventManager eventManager, TestEvent event, long expectedTimeMs) {
		OptionalLong eventDelayMs = getDistanceToNearestEventTime(eventManager, event, expectedTimeMs);
		Assertions.assertTrue(eventDelayMs.isPresent(), "Did not encounter event '" + event + "'");
		Assertions.assertTrue(eventDelayMs.getAsLong() <= PRECISION_MS, "Did not encounter event '" + event + "' approximately after " + expectedTimeMs + " ms");
	}

	private static OptionalLong getDistanceToNearestEventTime(TestEventManager eventManager, TestEvent event, long expectedTimeMs) {
		List<Long> eventTimesMs = eventManager.getElapsedTimesMs(event);
		return eventTimesMs.stream()
			.mapToLong(timeMs -> Math.abs(timeMs - expectedTimeMs))
			.min();
	}

	private static void checkPendingResourceEvents(TestEventManager eventManager, Collection<TaskDescription> taskDescriptions) {
		for (TaskDescription taskDescription : taskDescriptions) {
			PendingResourceEvent pendingResourceAddedEvent = new PendingResourceEvent(taskDescription, PendingResourceEvent.State.ADDED);
			PendingResourceEvent pendingResourceRemovedEvent = new PendingResourceEvent(taskDescription, PendingResourceEvent.State.REMOVED);
			List<Long> addTimes = eventManager.getElapsedTimesMs(pendingResourceAddedEvent);
			List<Long> removeTimes = eventManager.getElapsedTimesMs(pendingResourceRemovedEvent);
			int numAddEvents = addTimes.size();
			Assertions.assertEquals(numAddEvents, removeTimes.size(), "Number of removals of pending resources of task '" + taskDescriptions + "' does not match number of adds");
			for (int i = 0; i < numAddEvents; i++) {
				Assertions.assertTrue(addTimes.get(i) <= removeTimes.get(i), "Pending resource for task '" + taskDescription + "'  has been removed earlier than it has been added");
			}
		}
	}

	private static void checkResourceEvents(TestEventManager eventManager, Collection<TaskDescription> taskDescriptions) {
		for (TaskDescription taskDescription : taskDescriptions) {
			ResourceEvent resourceAcquiredEvent = new ResourceEvent(taskDescription, ResourceEvent.State.ACQUIRED);
			ResourceEvent resourceReleasedEvent = new ResourceEvent(taskDescription, ResourceEvent.State.RELEASED);
			List<Long> acquisitionTimes = eventManager.getElapsedTimesMs(resourceAcquiredEvent);
			List<Long> releaseTimes = eventManager.getElapsedTimesMs(resourceReleasedEvent);
			int numResourceAcquisition = acquisitionTimes.size();
			Assertions.assertTrue(numResourceAcquisition <= 1, "Resource acquired more than once for task '" + taskDescription + "'");
			Assertions.assertEquals(numResourceAcquisition, releaseTimes.size(), "Wrong number of resource releases by task '" + taskDescriptions + "'");
			for (int i = 0; i < numResourceAcquisition; i++) {
				Assertions.assertTrue(acquisitionTimes.get(i) <= releaseTimes.get(i), "Resource for task '" + taskDescription + "' released earlier than it has been acquired");
			}
		}
	}

	private static int getThreadIndex(Collection<TaskDescription> taskDescriptions) {
		Assertions.assertFalse(taskDescriptions.isEmpty(), "Internal error: No task descriptions specified");
		int threadIndex = taskDescriptions.iterator().next().getThreadIndex();
		for (TaskDescription taskDescription : taskDescriptions) {
			Assertions.assertEquals(threadIndex, taskDescription.getThreadIndex(), "Internal error: Deviating thread index");
		}
		return threadIndex;
	}

	private enum Behavior
	{
		TERMINATE_REGULARLY,
		TERMINATE_EXCEPTIONALLY,
		STOP_MANUALLY
	}
}
