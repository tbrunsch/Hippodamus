package dd.kms.hippodamus.parallelism;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.stream.IntStream;

@RunWith(Parameterized.class)
public class MaximumParallelismTest
{
	private static final int	NUM_TASKS			= 100;
	private static final long	TASK_TIME_MS		= 10;

	@Parameters(name = "max parallelism: {0}")
	public static Object getParameters() {
		final int maxParallelism = Math.min(TestUtils.getDefaultParallelism(), 4);
		return IntStream.range(1, maxParallelism + 1).boxed().toArray();
	}

	private final int	maxParallelism;

	private int			numRunningTasks;
	private int			maxNumRunningTasks;

	public MaximumParallelismTest(int maxParallelism) {
		this.maxParallelism = maxParallelism;
	}

	@Test
	public void testMaximumParallelism() {
		maxNumRunningTasks = 0;
		ExecutionCoordinatorBuilder<?> builder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.REGULAR, maxParallelism);
		try (ExecutionCoordinator coordinator = builder.build()) {
			for (int i = 0; i < NUM_TASKS; i++) {
				coordinator.execute(this::runTask);
			}
		}
		Assert.assertEquals("Unexpected maximum number of tasks executed in parallel", maxParallelism, maxNumRunningTasks);
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
