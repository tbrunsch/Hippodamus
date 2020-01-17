package dd.kms.hippodamus.execution;

import java.util.concurrent.*;

public class ExecutorServiceWrapper implements AutoCloseable
{
	public static final ExecutorServiceWrapper	COMMON_FORK_JOIN_POOL_WRAPPER	= new ExecutorServiceWrapper(ForkJoinPool.commonPool(), false);

	public static ExecutorServiceWrapper create(int numThreads) {
		return new ExecutorServiceWrapper(Executors.newWorkStealingPool(numThreads), true);
	}

	public static ExecutorServiceWrapper create(ExecutorService executorService, boolean shutdownRequired) {
		return new ExecutorServiceWrapper(executorService, shutdownRequired);
	}

	private final ExecutorService	executorService;
	private final boolean			shutdownRequired;

	private ExecutorServiceWrapper(ExecutorService executorService, boolean shutdownRequired) {
		this.executorService = executorService;
		this.shutdownRequired = shutdownRequired;
	}

	public <V> Future<V> submit(Callable<V> callable) {
		return executorService.submit(callable);
	}

	@Override
	public synchronized void close() {
		if (shutdownRequired) {
			executorService.shutdownNow();
		}
	}
}
