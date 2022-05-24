package dd.kms.hippodamus.testUtils.events;

import dd.kms.hippodamus.testUtils.states.CoordinatorState;

import java.util.Objects;

public class CoordinatorEvent extends TestEvent
{
	private final CoordinatorState	state;

	public CoordinatorEvent(CoordinatorState state) {
		this.state = state;
	}

	public CoordinatorState getState() {
		return state;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CoordinatorEvent that = (CoordinatorEvent) o;
		return state == that.state;
	}

	@Override
	public int hashCode() {
		return Objects.hash(state);
	}
}
