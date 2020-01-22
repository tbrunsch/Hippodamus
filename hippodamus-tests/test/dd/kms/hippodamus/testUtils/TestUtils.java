package dd.kms.hippodamus.testUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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

	public static <T> T createNamedInstance(Class<T> instanceInterface, T unnamedInstance, String name) {
		InvocationHandler invocationHandler = (proxy, method, args) ->
			"toString".equals(method.getName()) && (args == null || args.length == 0) ? name : method.invoke(unnamedInstance, args);
		return (T) Proxy.newProxyInstance(instanceInterface.getClassLoader(), new Class[]{instanceInterface}, invocationHandler);
	}
}
