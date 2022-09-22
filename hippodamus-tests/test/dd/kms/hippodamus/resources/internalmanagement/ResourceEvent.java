package dd.kms.hippodamus.resources.internalmanagement;

import dd.kms.hippodamus.testUtils.events.TestEvent;

import java.util.Objects;

/**
 * This event is fired when a resource is acquired, rejected, or released, depending on {@link ResourceEvent.State}.
 */
class ResourceEvent extends TestEvent
{
	private final TaskDescription	taskDescription;
	private final State				state;

	ResourceEvent(TaskDescription taskDescription, State state) {
		this.taskDescription = taskDescription;
		this.state = state;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ResourceEvent that = (ResourceEvent) o;
		return Objects.equals(taskDescription, that.taskDescription) &&
			state == that.state;
	}

	@Override
	public int hashCode() {
		return Objects.hash(taskDescription, state);
	}

	@Override
	public String toString() {
		return "Resource for " + taskDescription + " " + state;
	}

	enum State {ACQUIRED, REJECTED, RELEASED}
}
