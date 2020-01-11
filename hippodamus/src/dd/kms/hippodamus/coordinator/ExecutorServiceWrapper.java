package dd.kms.hippodamus.coordinator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

class ExecutorServiceWrapper implements AutoCloseable
{
	static final ExecutorServiceWrapper	COMMON_FORK_JOIN_POOL_WRAPPER	= new ExecutorServiceWrapper(ForkJoinPool.commonPool(), false);

	static ExecutorServiceWrapper create(int numThreads) {
		return new ExecutorServiceWrapper(Executors.newWorkStealingPool(numThreads), true);
	}

	private final ExecutorService	executorService;
	private final boolean			shutdownRequired;

	ExecutorServiceWrapper(ExecutorService executorService, boolean shutdownRequired) {
		this.executorService = executorService;
		this.shutdownRequired = shutdownRequired;
	}

	ExecutorService getExecutorService() {
		return executorService;
	}

	@Override
	public synchronized void close() {
		if (shutdownRequired) {
			executorService.shutdownNow();
		}
	}
}
