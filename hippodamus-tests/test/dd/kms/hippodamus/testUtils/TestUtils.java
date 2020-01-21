package dd.kms.hippodamus.testUtils;

import java.util.concurrent.ForkJoinPool;

public class TestUtils
{
	public static final boolean[]	BOOLEANS	= { false, true };

	public static void sleep(long timeMs) {
		if (timeMs == 0) {
			return;
		}
		try {
			Thread.sleep(timeMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Unexpected thread interruption", e);
		}
	}

	public static void waitForEmptyCommonForkJoinPool() {
		ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
		while (!forkJoinPool.isQuiescent()) {
			TestUtils.sleep(100);
		}
	}
}
