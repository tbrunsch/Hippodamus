package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

/**
 * This task limits the effective parallelism with which tasks are executed in parallel in two ways: By explicitly
 * specifying the maximum parallelism and by specifying a scarce resource each task tries to acquire. The resource and
 * the task requirements are chosen such that at most the resource's capacity many tasks can be executed in parallel.
 */
class ResourceVsParallelismTest
{
	private static final long	TASK_TIME_MS		= 500;
	private static final long	TIME_PER_TEST_MS	= 2000;
	private static final long	PRECISION_MS		= 400;

	@ParameterizedTest(name = "parallelism: {0}, resource capacity: {1}")
	@MethodSource("getParameters")
	void testResourceVsParallelism(int parallelism, int resourceCapacity) {
		CountableResource resource = new DefaultCountableResource("Resource", resourceCapacity);
		int expectedParallelism = Math.min(parallelism, resourceCapacity);
		int numTasks = Math.toIntExact(TIME_PER_TEST_MS / TASK_TIME_MS) * expectedParallelism;
		TestUtils.waitForEmptyCommonForkJoinPool();
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.COMPUTATIONAL, parallelism);
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			for (int i = 0; i < numTasks; i++) {
				final int taskIndex = i;
				coordinator.configure().requiredResource(resource, () -> 1L).execute(() -> executeTask(taskIndex));
			}
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();
		long expectedDurationMs = (long) Math.ceil(1.0 * numTasks / expectedParallelism) * TASK_TIME_MS;
		TestUtils.assertTimeBounds(expectedDurationMs, PRECISION_MS, elapsedTimeMs);
	}

	private void executeTask(int taskIndex) {
		TestUtils.simulateWork(TASK_TIME_MS);
	}

	static Object getParameters() {
		int maxParallelism = TestUtils.getDefaultParallelism();
		List<Object[]> parameters = new ArrayList<>();
		for (int parallelism = 1; parallelism <= maxParallelism; parallelism++) {
			for (int resourceCapacity = 1; resourceCapacity <= maxParallelism + 1; resourceCapacity++) {
				parameters.add(new Object[]{ parallelism, resourceCapacity });
			}
		}
		return parameters;
	}
}
