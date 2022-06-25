package dd.kms.hippodamus;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * The internally used {@link ExecutorService} may throw a {@link RejectedExecutionException} for whatever reason,
 * usually because internal resources have been exhausted. Hippodamus currently does not provide a strategy to avoid
 * this problem.<br>
 * <br>
 * This test checks that such exceptions at least do not occur in reasonable scenarios for different kinds of
 * {@code ExecutorService}s.
 */
class RejectedExecutionExceptionTest
{
	private static final int	NUM_THREADS		= 2;
	private static final int	NUM_TASKS		= 1000;
	private static final long	TASK_TIME_MS	= 10;

	@ParameterizedTest(name = "executor service: {0}")
	@MethodSource("getExecutorServiceSuppliers")
	void testRejectedExecutionException(Supplier<ExecutorService> executorServiceSupplier) {
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.COMPUTATIONAL, executorServiceSupplier.get(), true);
		try (ExecutionCoordinator coordinator = builder.build()) {
			for (int i = 0; i < NUM_TASKS; i++) {
				coordinator.execute(() -> TestUtils.simulateWork(TASK_TIME_MS));
			}
		}
	}

	static Object getExecutorServiceSuppliers() {
		return new Object[]{
			TestUtils.COMMON_FORK_JOIN_POOL_SUPPLIER,
			TestUtils.WORK_STEALING_POOL_SUPPLIER,
			TestUtils.createFixedThreadPoolSupplier(NUM_THREADS)
		};
	}
}
