package dd.kms.hippodamus.testUtils;

import dd.kms.hippodamus.api.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.coordinator.TestAggregationCoordinator;
import dd.kms.hippodamus.testUtils.coordinator.TestExecutionCoordinator;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.ForkJoinPool;

public class TestUtils
{
	public static final boolean[]	BOOLEANS	= {false, true};

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

	public static void assertTimeBounds(long expectedLowerBoundMs, long expectedIntervalLength, long elapsedTimeMs) {
		assertTimeLowerBound(expectedLowerBoundMs, elapsedTimeMs);
		assertTimeUpperBound(expectedLowerBoundMs + expectedIntervalLength, elapsedTimeMs);
	}


	public static void assertTimeBounds(long expectedLowerBoundMs, long expectedIntervalLength, long elapsedTimeMs, String description) {
		assertTimeLowerBound(expectedLowerBoundMs, elapsedTimeMs, description);
		assertTimeUpperBound(expectedLowerBoundMs + expectedIntervalLength, elapsedTimeMs, description);
	}

	private static void assertTimeLowerBound(long expectedLowerBoundMs, long elapsedTimeMs) {
		assertTimeLowerBound(expectedLowerBoundMs, elapsedTimeMs, "Coordinator");
	}

	private static void assertTimeLowerBound(long expectedLowerBoundMs, long elapsedTimeMs, String description) {
		Assertions.assertTrue(expectedLowerBoundMs <= elapsedTimeMs,
			description + " required " + elapsedTimeMs + " ms, but it should have required at least " + expectedLowerBoundMs + " ms");
	}

	public static void assertTimeUpperBound(long expectedUpperBoundMs, long elapsedTimeMs) {
		assertTimeUpperBound(expectedUpperBoundMs, elapsedTimeMs, "Coordinator");
	}

	public static void assertTimeUpperBound(long expectedUpperBoundMs, long elapsedTimeMs, String description) {
		Assertions.assertTrue(elapsedTimeMs <= expectedUpperBoundMs,
			description + " required " + elapsedTimeMs + " ms, but it should have required at most " + expectedUpperBoundMs + " ms");
	}

	public static <T> T createNamedInstance(Class<T> instanceInterface, T unnamedInstance, String name) {
		InvocationHandler invocationHandler = (proxy, method, args) ->
			"toString".equals(method.getName()) && (args == null || args.length == 0) ? name : method.invoke(unnamedInstance, args);
		return instanceInterface.cast(Proxy.newProxyInstance(instanceInterface.getClassLoader(), new Class[]{instanceInterface}, invocationHandler));
	}

	public static TestExecutionCoordinator wrap(ExecutionCoordinator coordinator, TestEventManager eventManager) {
		return new TestExecutionCoordinator(coordinator, eventManager);
	}

	public static <S, R> TestAggregationCoordinator<S, R> wrap(AggregationCoordinator<S, R> coordinator, TestEventManager eventManager) {
		return new TestAggregationCoordinator(coordinator, eventManager);
	}
}
