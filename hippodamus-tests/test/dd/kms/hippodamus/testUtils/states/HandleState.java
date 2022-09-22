package dd.kms.hippodamus.testUtils.states;

public enum HandleState
{
	STARTED,
	STOPPED,
	COMPLETED,
	TERMINATED_EXCEPTIONALLY,

	/**
	 * A {@link dd.kms.hippodamus.testUtils.events.HandleEvent} with this state is automatically
	 * raised by the {@link dd.kms.hippodamus.testUtils.coordinator.BaseTestCoordinator} when a
	 * {@code HandleEvent} of state {@link #COMPLETED} or {@link #TERMINATED_EXCEPTIONALLY} occurs.
	 */
	TERMINATED
}
