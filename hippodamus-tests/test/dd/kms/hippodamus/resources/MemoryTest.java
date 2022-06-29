package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Executing tasks in parallel must also take other resources, primarily main memory, into consideration. This test
 * shows that running memory intensive tasks in parallel can lead to an {@link OutOfMemoryError} and that this can be
 * avoided when the memory consumption of each task is known and considered.
 */
class MemoryTest
{
	private static final String[]	MEMORY_UNITS	= { "Bytes", "kB", "MB", "GB", "TB" };

	@ParameterizedTest(name = "consider memory consumption: {0}")
	@MethodSource("getConsiderMemoryConsumptionValues")
	public void testOutOfMemoryError(boolean considerMemoryConsumption) {
		TaskParameters taskParameters = getTaskParameters();
		taskParameters.printSetup();

		TestUtils.waitForEmptyCommonForkJoinPool();

		boolean outOfMemoryErrorOccurred = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < taskParameters.getNumberOfTasks(); i++) {
				ExecutionConfigurationBuilder builder = coordinator.configure();
				if (considerMemoryConsumption) {
					builder.executionController(MemoryResource.getShare(taskParameters.getTaskSize()));
				}
				builder.execute(() -> executeTask(taskParameters));
			}
		} catch (OutOfMemoryError e) {
			outOfMemoryErrorOccurred = true;
		}
		if (considerMemoryConsumption) {
			Assertions.assertFalse(outOfMemoryErrorOccurred, "Unexpected OutOfMemoryError");
		} else {
			Assertions.assertTrue(outOfMemoryErrorOccurred, "Expected an OutOfMemoryError");
		}
	}

	private TaskParameters getTaskParameters() {
		forceGc();
		try {
			return new TaskParameters();
		} catch (IllegalStateException e) {
			Assumptions.assumeTrue(false, e.getMessage());
			throw e;
		}
	}

	static Object getConsiderMemoryConsumptionValues() {
		return TestUtils.BOOLEANS;
	}

	private static void executeTask(TaskParameters taskParameters) {
		int chunkSize = taskParameters.getChunkSize();
		int chunkAllocationDelayMs = taskParameters.getChunkAllocationDelayMs();
		List<byte[]> bytes = new ArrayList<>();
		long numChunks = taskParameters.getNumberOfChunksPerTask();
		for (long i = 0; i < numChunks; i++) {
			bytes.add(new byte[chunkSize]);
			try {
				Thread.sleep(chunkAllocationDelayMs);
			} catch (InterruptedException e) {
				return;
			}
		}
		forceGc();
	}

	private static void forceGc() {
		for (int i = 0; i < 3; i++) {
			System.gc();
		}
	}

	private static String formatMemory(long sizeInBytes) {
		double size = sizeInBytes;
		for (int i = 0; ; i++) {
			if (size < 1024 || i == MEMORY_UNITS.length - 1) {
				return String.format("%.2f %s", size, MEMORY_UNITS[i]);
			}
			size /= 1024;
		}
	}

	private static class TaskParameters
	{
		private static final int	NUMBER_OF_TASKS_OVER_PARALLELISM	= 4;
		private static final int	PREFERRED_NUMBER_OF_CHUNKS			= 10;
		private static final int	CHUNK_ALLOCATION_DELAY_MS			= 500;

		private final int	parallelism;
		private final long	availableMemory;
		private final int	numTasks;
		private final long	taskSize;
		private final int	chunkSize;
		private final long	numChunksPerTask;

		private TaskParameters() {
			parallelism = TestUtils.getDefaultParallelism();
			if (parallelism < 2) {
				throw new IllegalStateException("Cannot run OutOfMemory tests because the common ForkJoinPool has parallelism < 2");
			}

			Runtime runtime = Runtime.getRuntime();
			long maxMemory = runtime.maxMemory();
			if (maxMemory == Long.MAX_VALUE) {
				throw new IllegalStateException("Cannot run OutOfMemory tests because no max heap size is defined");
			}
			long allocatedMemory = runtime.totalMemory() - runtime.freeMemory();
			availableMemory = maxMemory - allocatedMemory;
			numTasks = NUMBER_OF_TASKS_OVER_PARALLELISM*parallelism;

			// ensure that parallelism many tasks will require more memory than available
			taskSize = (long) Math.ceil(1.1 * availableMemory / parallelism);
			chunkSize = Math.toIntExact(Math.min(taskSize / PREFERRED_NUMBER_OF_CHUNKS, Integer.MAX_VALUE));
			numChunksPerTask = taskSize / chunkSize;
		}

		int getNumberOfTasks() {
			return numTasks;
		}

		long getTaskSize() {
			return taskSize;
		}

		int getChunkSize() {
			return chunkSize;
		}

		long getNumberOfChunksPerTask() {
			return numChunksPerTask;
		}

		int getChunkAllocationDelayMs() {
			return CHUNK_ALLOCATION_DELAY_MS;
		}

		void printSetup() {
			System.out.println("Parallelism: " + parallelism);
			System.out.println("Available memory: " + formatMemory(availableMemory));
			System.out.println("Task sizes: " + formatMemory(taskSize));
			System.out.println("Number of tasks: " + numTasks);
			System.out.println("Number of chunks to allocate per task: " + numChunksPerTask);
		}
	}
}
