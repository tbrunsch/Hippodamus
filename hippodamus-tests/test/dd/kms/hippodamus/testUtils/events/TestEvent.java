package dd.kms.hippodamus.testUtils.events;

public abstract class TestEvent
{
	/**
	 * Used to query events. Hence, only what the user might know should be considered by
	 * {@code equals()}.
	 */
	@Override
	public abstract boolean equals(Object o);

	/**
	 * Used to query events. Hence, only what the user might know should be considered by
	 * {@code hashCode()}.
	 */
	@Override
	public abstract int hashCode();
}
