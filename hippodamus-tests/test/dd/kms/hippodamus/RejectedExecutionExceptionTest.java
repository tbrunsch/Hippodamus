package dd.kms.hippodamus;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * This test checks that the framework does not suffer from {@link RejectedExecutionException}s.
 */
@RunWith(Parameterized.class)
public class RejectedExecutionExceptionTest
{
	private static final int						NUM_THREADS		= 2;
	private static final int						NUM_TASKS		= 1000;
	private static final long						TASK_TIME_MS	= 10;

	private static final Supplier<ExecutorService>	COMMON_FORK_JOIN_POOL_SUPPLIER		= TestUtils.createNamedInstance(Supplier.class, ForkJoinPool::commonPool, "common fork join pool");
	private static final Supplier<ExecutorService>	DEDICATED_EXECUTOR_SERVICE_SUPPLIER	= TestUtils.createNamedInstance(Supplier.class, () -> Executors.newFixedThreadPool(NUM_THREADS), "dedicated executor service");

	@Parameterized.Parameters(name = "executor service: {0}")
	public static Object getParameters() {
		return new Object[]{ COMMON_FORK_JOIN_POOL_SUPPLIER, DEDICATED_EXECUTOR_SERVICE_SUPPLIER };
	}

	private final Supplier<ExecutorService>	executorServiceSupplier;

	public RejectedExecutionExceptionTest(Supplier<ExecutorService> executorServiceSupplier) {
		this.executorServiceSupplier = executorServiceSupplier;
	}

	@Test
	public void testRejectedExecutionException() {
		ExecutionCoordinatorBuilder<?> builder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.REGULAR, executorServiceSupplier.get(), true);
		try (ExecutionCoordinator coordinator = builder.build()) {
			for (int i = 0; i < NUM_TASKS; i++) {
				coordinator.execute(() -> TestUtils.simulateWork(TASK_TIME_MS));
			}
		}
	}
}
