package dd.kms.hippodamus.testUtils.events;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.states.HandleState;

import java.util.Objects;

public class HandleEvent extends TestEvent
{
	private final Handle		handle;
	private final HandleState	state;
	private final Throwable		exception;

	public HandleEvent(Handle handle, HandleState state, Throwable exception) {
		this.handle = handle;
		this.state = state;
		this.exception = exception;
	}

	public Handle getHandle() {
		return handle;
	}

	public HandleState getState() {
		return state;
	}

	public Throwable getException() {
		return exception;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		HandleEvent that = (HandleEvent) o;

		// do not consider exception and timestamp of super class
		return handle == that.handle && state == that.state;
	}

	@Override
	public int hashCode() {
		// do not consider exception and timestamp of super class
		return Objects.hash(handle, state);
	}
}
