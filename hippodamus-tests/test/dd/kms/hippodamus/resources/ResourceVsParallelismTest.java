package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

/**
 * This task limits the effective parallelism with which tasks are executed
 * in parallel in two ways: By explicitly specifying the maximum parallelism
 * and by specifying a scarce resource that has been acquired by each task.
 * The resource and the task requirements are chosen such that at most the
 * resource's capacity many tasks can be executed in parallel.
 */
@RunWith(Parameterized.class)
public class ResourceVsParallelismTest
{
	private static final long	TASK_TIME_MS		= 500;
	private static final long	TIME_PER_TEST_MS	= 2000;
	private static final long	PRECISION_MS		= 300;

	private final int	parallelism;
	private final int	resourceCapacity;

	public ResourceVsParallelismTest(int parallelism, int resourceCapacity) {
		this.parallelism = parallelism;
		this.resourceCapacity = resourceCapacity;
	}

	@Parameterized.Parameters(name = "parallelism: {0}, resource capacity: {1}")
	public static Object getParameters() {
		int maxParallelism = TestUtils.getDefaultParallelism();
		List<Object[]> parameters = new ArrayList<>();
		for (int parallelism = 1; parallelism <= maxParallelism; parallelism++) {
			for (int resourceCapacity = 1; resourceCapacity <= maxParallelism + 1; resourceCapacity++) {
				parameters.add(new Object[]{ parallelism, resourceCapacity });
			}
		}
		return parameters;
	}

	@Test
	public void testResourceVsParallelism() {
		Resource<Long> resource = Resources.newCountableResource("Resource", resourceCapacity);
		int expectedParallelism = Math.min(parallelism, resourceCapacity);
		int numTasks = Math.toIntExact(TIME_PER_TEST_MS / TASK_TIME_MS) * expectedParallelism;
		TestUtils.waitForEmptyCommonForkJoinPool();
		ExecutionCoordinatorBuilder<?> coordinatorBuilder = Coordinators.configureExecutionCoordinator().maximumParallelism(TaskType.REGULAR, parallelism);
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			for (int i = 0; i < numTasks; i++) {
				final int taskIndex = i;
				coordinator.configure().requires(resource, 1L).execute(() -> executeTask(taskIndex));
			}
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();
		long expectedDurationMs = (long) Math.ceil(1.0 * numTasks / expectedParallelism) * TASK_TIME_MS;
		TestUtils.assertTimeLowerBound(expectedDurationMs, elapsedTimeMs);
		TestUtils.assertTimeUpperBound(expectedDurationMs + PRECISION_MS, elapsedTimeMs);
	}

	private void executeTask(int taskIndex) {
		TestUtils.simulateWork(TASK_TIME_MS);
	}
}
