package dd.kms.hippodamus.testUtils.events;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.states.HandleState;

import java.util.Objects;

public class HandleEvent extends TestEvent
{
	private final Handle						handle;
	private final HandleState					state;

	public HandleEvent(Handle handle, HandleState state) {
		this.handle = handle;
		this.state = state;
	}

	public Handle getHandle() {
		return handle;
	}

	public HandleState getState() {
		return state;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		HandleEvent that = (HandleEvent) o;
		return handle == that.handle && state == that.state;
	}

	@Override
	public int hashCode() {
		return Objects.hash(handle, state);
	}

	@Override
	public String toString() {
		return handle.getTaskName() + ": " + state;
	}
}
