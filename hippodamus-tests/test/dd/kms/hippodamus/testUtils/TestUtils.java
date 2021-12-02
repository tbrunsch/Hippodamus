package dd.kms.hippodamus.testUtils;

import org.junit.Assert;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.ForkJoinPool;

public class TestUtils
{
	public static final boolean[]	BOOLEANS	= { false, true };

	public static void simulateWork(long timeMs) {
		if (timeMs == 0) {
			return;
		}
		long endTimeMs = System.currentTimeMillis() + timeMs;
		while (System.currentTimeMillis() < endTimeMs);
	}

	public static void waitForEmptyCommonForkJoinPool() {
		ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
		while (!forkJoinPool.isQuiescent()) {
			Thread.yield();
		}
	}

	public static int getPotentialParallelism() {
		return Runtime.getRuntime().availableProcessors();
	}

	public static int getDefaultParallelism() {
		return ForkJoinPool.commonPool().getParallelism();
	}

	public static void assertTimeLowerBound(long expectedLowerBoundMs, long elapsedTimeMs) {
		assertTimeLowerBound(expectedLowerBoundMs, elapsedTimeMs, "Coordinator");
	}

	public static void assertTimeLowerBound(long expectedLowerBoundMs, long elapsedTimeMs, String description) {
		Assert.assertTrue(description + " required " + elapsedTimeMs + " ms, but it should have required at least " + expectedLowerBoundMs + " ms", expectedLowerBoundMs <= elapsedTimeMs);
	}

	public static void assertTimeUpperBound(long expectedUpperBoundMs, long elapsedTimeMs) {
		assertTimeUpperBound(expectedUpperBoundMs, elapsedTimeMs, "Coordinator");
	}

	public static void assertTimeUpperBound(long expectedUpperBoundMs, long elapsedTimeMs, String description) {
		Assert.assertTrue(description + " required " + elapsedTimeMs + " ms, but it should have required at most " + expectedUpperBoundMs + " ms", elapsedTimeMs <= expectedUpperBoundMs);
	}

	public static <T> T createNamedInstance(Class<T> instanceInterface, T unnamedInstance, String name) {
		InvocationHandler invocationHandler = (proxy, method, args) ->
			"toString".equals(method.getName()) && (args == null || args.length == 0) ? name : method.invoke(unnamedInstance, args);
		return instanceInterface.cast(Proxy.newProxyInstance(instanceInterface.getClassLoader(), new Class[]{instanceInterface}, invocationHandler));
	}
}
