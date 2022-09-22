package dd.kms.hippodamus.resources.internalmanagement;

import dd.kms.hippodamus.testUtils.events.TestEvent;

import java.util.Objects;

/**
 * This event is fired when a resource is added to or removed from the pending resources. This depends on
 * {@link PendingResourceEvent.State}.
 */
class PendingResourceEvent extends TestEvent
{
	private final TaskDescription	taskDescription;
	private final State				state;

	PendingResourceEvent(TaskDescription taskDescription, State state) {
		this.taskDescription = taskDescription;
		this.state = state;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PendingResourceEvent that = (PendingResourceEvent) o;
		return state == that.state &&
			Objects.equals(taskDescription, that.taskDescription);
	}

	@Override
	public int hashCode() {
		return Objects.hash(taskDescription, state);
	}

	@Override
	public String toString() {
		return "Pending resource share for " + taskDescription + " " + state;
	}

	enum State {ADDED, REMOVED}
}
