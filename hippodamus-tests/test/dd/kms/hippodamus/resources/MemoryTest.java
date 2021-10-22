package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Executing tasks in parallel must also take other resources, mainly main memory,
 * into consideration. This test shows that running strongly memory consuming tasks
 * in parallel leads to an {@link OutOfMemoryError} and that this can be avoided
 * when for each task it is specified how much memory it consumes.
 */
public class MemoryTest
{
	private static final String[]	MEMORY_UNITS	= { "Bytes", "kB", "MB", "GB", "TB" };

	@Test
	public void testOutOfMemoryError() {
		TaskParameters taskParameters = getTaskParameters();
		taskParameters.printSetup();

		boolean outOfMemoryErrorOccurred = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < taskParameters.getNumberOfTasks(); i++) {
				final int taskIndex = i;
				coordinator.execute(() -> executeTask(taskIndex, taskParameters));
			}
		} catch (OutOfMemoryError e) {
			outOfMemoryErrorOccurred= true;
		}
		Assert.assertTrue("Expected an OutOfMemoryError", outOfMemoryErrorOccurred);
	}

	@Test
	public void testNoOutOfMemoryError() {
		TaskParameters taskParameters = getTaskParameters();
		taskParameters.printSetup();

		long estimatedAvailableMemory = Math.round(0.9 * taskParameters.getAvailableMemory());
		Resource<Long> memory = Resources.newCountableResource("Memory", estimatedAvailableMemory);
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < taskParameters.getNumberOfTasks(); i++) {
				final int taskIndex = i;
				coordinator.configure().requires(memory, taskParameters.getTaskSize()).execute(() -> executeTask(taskIndex, taskParameters));
			}
		} catch (OutOfMemoryError e) {
			Assert.fail("Unexpected OutOfMemoryError");
		}
	}

	private TaskParameters getTaskParameters() {
		forceGc();
		try {
			return new TaskParameters();
		} catch (IllegalStateException e) {
			Assume.assumeNoException(e.getMessage(), e);
			throw e;
		}
	}

	private static void executeTask(int taskIndex, TaskParameters taskParameters) {
		int chunkSize = taskParameters.getChunkSize();
		int chunkAllocationDelayMs = taskParameters.getChunkAllocationDelayMs();
		List<byte[]> bytes = new ArrayList<>();
		for (long i = 0; i < taskParameters.getNumberOfChunksPerTask(); i++) {
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

		int getParallelism() {
			return parallelism;
		}

		long getAvailableMemory() {
			return availableMemory;
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
