package dd.kms.hippodamus.testUtils;

/**
 * A very simple stop watch implementation that starts measuring
 * time after construction.
 */
public class StopWatch
{
	private final long	startMs;

	public StopWatch() {
		startMs = System.currentTimeMillis();
	}

	public long getElapsedTimeMs() {
		return System.currentTimeMillis() - startMs;
	}
}
