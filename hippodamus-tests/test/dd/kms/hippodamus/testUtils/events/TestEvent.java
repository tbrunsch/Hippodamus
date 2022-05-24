package dd.kms.hippodamus.testUtils.events;

public abstract class TestEvent
{
	private final long	timestamp	= System.currentTimeMillis();

	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Used to query events. Hence, only what the user might know should be considered by
	 * {@code equals()}. The timestamp is nothing he knows, but what he is interested in.
	 */
	@Override
	public abstract boolean equals(Object o);

	/**
	 * Used to query events. Hence, only what the user might know should be considered by
	 * {@code hashCode()}. The timestamp is nothing he knows, but what he is interested in.
	 */
	@Override
	public abstract int hashCode();
}
