package dd.kms.hippodamus.parallelism;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;

/**
 * This test verifies that the {@link ExecutionCoordinator} takes the configured maximum parallelism into account. The
 * test also verifies that the specified maximum parallelism can also be achieved under certain criteria that are met
 * in this test.
 */
class MaximumParallelismTest
{
	private static final int	NUM_TASKS		= 100;
	private static final long	TASK_TIME_MS	= 10;

	static Object getParameters() {
		int maxParallelism = Math.min(TestUtils.getDefaultParallelism(), 4);
		return IntStream.range(1, maxParallelism + 1).boxed().toArray();
	}

	private int			numRunningTasks;
	private int			maxNumRunningTasks;

	@ParameterizedTest(name = "max parallelism: {0}")
	@MethodSource("getParameters")
	void testMaximumParallelism(int maxParallelism) {
		maxNumRunningTasks = 0;
		ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.COMPUTATIONAL, maxParallelism);
		try (ExecutionCoordinator coordinator = builder.build()) {
			for (int i = 0; i < NUM_TASKS; i++) {
				coordinator.execute(this::runTask);
			}
		}
		Assertions.assertEquals(maxParallelism, maxNumRunningTasks, "Unexpected maximum number of tasks executed in parallel");
	}

	private void runTask() {
		synchronized (this) {
			numRunningTasks++;
			maxNumRunningTasks = Math.max(maxNumRunningTasks, numRunningTasks);
		}
		TestUtils.simulateWork(TASK_TIME_MS);
		synchronized (this) {
			numRunningTasks--;
		}
	}
}
