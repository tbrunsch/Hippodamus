package dd.kms.hippodamus.parallelism;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.stream.IntStream;

@RunWith(Parameterized.class)
public class NoDeadlockTest
{
	private static final long	LONG_TASK_TIME_MS	= 500;
	private static final long	PRECISION_MS		= 200;

	@Parameters(name = "max parallelism: {0}")
	public static Object getParameters() {
		final int maxParallelism = Math.min(TestUtils.getDefaultParallelism(), 4);
		return IntStream.range(1, maxParallelism + 1).boxed().toArray();
	}

	private final int	maxParallelism;

	public NoDeadlockTest(int maxParallelism) {
		this.maxParallelism = maxParallelism;
	}

	@Test
	public void testNoDeadlock() {
		Thread stoppingThread;	// used to stop coordinator in case of a dead lock
		ExecutionCoordinatorBuilder<?> builder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.REGULAR, maxParallelism);
		StopWatch stopWatch = new StopWatch();
		try (ExecutionCoordinator coordinator = builder.build()) {
			ResultHandle<Integer> task1 = coordinator.execute(() -> returnWithDelay(1));
			ResultHandle<Integer> task2 = coordinator.configure().dependencies(task1).execute(() -> returnWithDelay(task1.get() + 1));
			ResultHandle<Integer> task3 = coordinator.execute(() -> returnWithDelay(task2.get() + 1));
			ResultHandle<Integer> task4 = coordinator.execute(() -> returnWithDelay(task3.get() + 1));
			stoppingThread = new Thread(() -> {
				try {
					Thread.sleep(6*LONG_TASK_TIME_MS);
				} catch (InterruptedException e) {
					// expected
				}
				if (!task4.hasStopped()) {
					coordinator.stop();
				}
			});
			stoppingThread.start();
		}
		long elapsedTimeMs = stopWatch.getElapsedTimeMs();
		stoppingThread.interrupt();

		TestUtils.assertTimeUpperBound(4*LONG_TASK_TIME_MS + PRECISION_MS, elapsedTimeMs);
	}

	private int returnWithDelay(int value) {
		TestUtils.simulateWork(LONG_TASK_TIME_MS);
		return value;
	}
}
