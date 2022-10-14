package dd.kms.hippodamus.resources.memory;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.resources.CountableResource;
import dd.kms.hippodamus.resources.DefaultCountableResource;
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
	@ParameterizedTest(name = "{0}")
	@MethodSource("getResourceTypes")
	void testOutOfMemoryError(ResourceType resourceType) {
		TaskParameters taskParameters = getTaskParameters();
		taskParameters.printSetup();

		TestUtils.waitForEmptyCommonForkJoinPool();

		CountableResource resource = createResource(resourceType);

		boolean outOfMemoryErrorOccurred = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 0; i < taskParameters.getNumberOfTasks(); i++) {
				ExecutionConfigurationBuilder builder = coordinator.configure();
				builder.requiredResource(resource, () -> taskParameters.getTaskSize());
				builder.execute(() -> executeTask(taskParameters));
			}
		} catch (OutOfMemoryError e) {
			System.out.println("Caught out of memory exception");
			outOfMemoryErrorOccurred = true;
		}
		switch (resourceType) {
			case UNLIMITED:
				// memory constraints ignored
				Assertions.assertTrue(outOfMemoryErrorOccurred, "Expected an OutOfMemoryError");
				break;
			case MONITORED_MEMORY:
			case CONTROLLABLE_COUNTABLE_RESOURCE:
				// memory constraints considered
				Assertions.assertFalse(outOfMemoryErrorOccurred, "Unexpected OutOfMemoryError");
				break;
			default:
				throw new IllegalArgumentException("Unsupported resource type");
		}
	}

	private TaskParameters getTaskParameters() {
		MemoryUtils.forceGc();
		try {
			return new TaskParameters();
		} catch (IllegalStateException e) {
			Assumptions.assumeTrue(false, e.getMessage());
			throw e;
		}
	}

	private static CountableResource createResource(ResourceType resourceType) {
		switch (resourceType) {
			case UNLIMITED:
				return UnlimitedCountableResource.RESOURCE;
			case MONITORED_MEMORY:
				return MemoryResource.RESOURCE;
			case CONTROLLABLE_COUNTABLE_RESOURCE: {
				long estimatedAvailableMemory = MemoryUtils.estimateAvailableMemory(0.7);
				return new DefaultCountableResource("Countable resource for " + MemoryUtils.formatMemory(estimatedAvailableMemory), estimatedAvailableMemory);
			}
			default:
				throw new IllegalArgumentException("Unsupported resource type " + resourceType);
		}
	}

	static Object[] getResourceTypes() {
		return ResourceType.values();
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
		MemoryUtils.forceGc();
	}

	private enum ResourceType
	{
		UNLIMITED("Unlimited countable resource"),
		MONITORED_MEMORY("Monitored memory resource"),
		CONTROLLABLE_COUNTABLE_RESOURCE("Controllable countable resource");

		private final String	stringRepresentation;

		ResourceType(String stringRepresentation) {
			this.stringRepresentation = stringRepresentation;
		}

		@Override
		public String toString() {
			return stringRepresentation;
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

			availableMemory = MemoryUtils.estimateAvailableMemory(1.0);
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
			System.out.println("Available memory: " + MemoryUtils.formatMemory(availableMemory));
			System.out.println("Task sizes: " + MemoryUtils.formatMemory(taskSize));
			System.out.println("Number of tasks: " + numTasks);
		}
	}
}
