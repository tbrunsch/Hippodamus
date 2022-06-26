package dd.kms.hippodamus;

import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.TestUtils;
import dd.kms.hippodamus.testUtils.events.HandleEvent;
import dd.kms.hippodamus.testUtils.events.TestEventManager;
import dd.kms.hippodamus.testUtils.states.HandleState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * This test focuses on the following scenario: We have a transaction consisting of multiple parallel tasks that all
 * have to complete. If any of these tasks fails, then no further task should be started and the transaction should be
 * rewound.<br>
 * <br>
 * In this test, we simulate the creation of several files within a transaction. At some point in time, the file system
 * is pretended to be shut down, such that trials to create further files on that file system will fail. In that case,
 * the transaction has to be rewound, which means to delete all files that have been created so far.
 */
class TransactionTest
{
	private static final Transaction	TRANSACTION	= new Transaction(
		new CreateFileAction("File 1", 500),
		new CreateFileAction("File 2", 800),
		new CreateFileAction("File 3", 1300),
		new CreateFileAction("File 4", 700),
		new CreateFileAction("File 5", 1000),
		new CreateFileAction("File 6", 300),
		new CreateFileAction("File 7", 1500),
		new CreateFileAction("File 8", 600),
		new CreateFileAction("File 9", 500)
	);

	/**
	 * Expected schedule (incl. finishing times) for 2 threads:
	 *
	 * Thread 1: File 1 ----> 500, File 3 ------------> 1800, File 6 --> 2100, File 7 ----------------------------> 3600
	 * Thread 2: File 2 -------> 800, File 4 ------> 1500, File 5 ---------> 2500, File 8 -----> 3100, File 9 ---->3600
	 */
	private static final long[]			EXPECTED_START_TIMES_MS = {0, 0, 500, 800, 1500, 1800, 2100, 2500, 3100};

	private static final long[]			EXPECTED_END_TIMES_MS;

	private static final long			PRECISION_MS			= 300;

	static {
		List<CreateFileAction> actions = TRANSACTION.getActions();
		int numActions = actions.size();
		EXPECTED_END_TIMES_MS = new long[numActions];
		for (int i = 0; i < numActions; i++) {
			EXPECTED_END_TIMES_MS[i] = EXPECTED_START_TIMES_MS[i] + actions.get(i).getRequiredTimeMs();
		}
	}

	@ParameterizedTest(name = "transaction timeout after {0} ms")
	@MethodSource("getFileSystemAvailabilityTimeValues")
	void testCopyFiles(long fileSystemAvailabilityTimeMs) throws IOException {
		/*
		 * The file system will "shut down" after fileSystemAvailabilityTimeMs millis. Trying to create a file
		 * afterwards will result in an IOException.
		 */
		FileSystem fileSystem = new FileSystem(fileSystemAvailabilityTimeMs);
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.BLOCKING, Executors.newFixedThreadPool(2), true)
			.maximumParallelism(TaskType.BLOCKING, 2);
		boolean caughtException = false;
		List<CreateFileAction> actions = TRANSACTION.getActions();
		List<Handle> tasks = new ArrayList<>();
		TestEventManager eventManager = new TestEventManager();
		try (ExecutionCoordinator coordinator = TestUtils.wrap(coordinatorBuilder.build(), eventManager)) {
			for (CreateFileAction action : actions) {
				Handle task = coordinator.configure().taskType(TaskType.BLOCKING).execute(() -> action.apply(fileSystem));
				tasks.add(task);
			}
		} catch (IOException e) {
			caughtException = true;
		}
		long lastEndTimeMs = Arrays.stream(EXPECTED_END_TIMES_MS).max().getAsLong();
		boolean expectedException = fileSystemAvailabilityTimeMs <= lastEndTimeMs;

		List<Handle> exceptionalTasks = tasks.stream().filter(task -> task.getException() instanceof IOException).collect(Collectors.toList());
		if (expectedException) {
			Assertions.assertTrue(caughtException, "An IOException has been swallowed");
			Assertions.assertFalse(exceptionalTasks.isEmpty(), "No IOException has been encountered in any of the tasks");
		} else {
			Assertions.assertFalse(caughtException, "Unexpected IOException");
			Assertions.assertTrue(exceptionalTasks.isEmpty(), "An IOException has been encountered in some tasks");
		}

		int numTasks = tasks.size();
		for (int i = 0; i < numTasks; i++) {
			CreateFileAction taskAction = actions.get(i);
			Handle task = tasks.get(i);
			long expectedStartTimeMs = EXPECTED_START_TIMES_MS[i];
			long expectedEndTimeMs = EXPECTED_END_TIMES_MS[i];
			HandleEvent taskStartedEvent = new HandleEvent(task, HandleState.STARTED);
			if (expectedEndTimeMs <= fileSystemAvailabilityTimeMs) {
				// task should have been executed
				Assertions.assertTrue(taskAction.hasPerformed(), "The action of task " + i + " should have been performed");
				Assertions.assertTrue(fileSystem.getFiles().contains(taskAction.getFileName()), "File '" + taskAction.getFileName() + "' does not exist");
				Assertions.assertNull(task.getException(), "Task " + i + " should not have thrown an exception");
				HandleEvent taskCompletedEvent = new HandleEvent(task, HandleState.COMPLETED);
				TestUtils.assertTimeBounds(expectedStartTimeMs, PRECISION_MS, eventManager.getElapsedTimeMs(taskStartedEvent), "Start of task " + i);
				TestUtils.assertTimeBounds(expectedEndTimeMs, PRECISION_MS, eventManager.getElapsedTimeMs(taskCompletedEvent), "Completion of task " + i);
			} else if (expectedStartTimeMs > fileSystemAvailabilityTimeMs) {
				// task should not even have started
				Assertions.assertFalse(taskAction.hasTriedToPerform(), "The action of task " + i + " should not have been tried to be performed");
				Assertions.assertFalse(fileSystem.getFiles().contains(taskAction.getFileName()), "File '" + taskAction.getFileName() + "' should not exist");
				Assertions.assertFalse(eventManager.encounteredEvent(taskStartedEvent), "Task " + i + " should not started");
				Assertions.assertNull(task.getException(), "Task " + i + " should not have thrown an exception");
			} else {
				// task should have started, but then terminated exceptionally
				Assertions.assertTrue(taskAction.hasTriedToPerform(), "The action of task " + i + " should have been tried to be performed");
				Assertions.assertFalse(taskAction.hasPerformed(), "The action of task " + i + " should not have been finished");
				Assertions.assertFalse(fileSystem.getFiles().contains(taskAction.getFileName()), "File '" + taskAction.getFileName() + "' should not exist");
				Assertions.assertTrue(task.getException() instanceof IOException, "Task " + i + " should have thrown an IOException");
				HandleEvent taskTerminatedExceptionallyEvent = new HandleEvent(task, HandleState.TERMINATED_EXCEPTIONALLY);
				TestUtils.assertTimeBounds(expectedStartTimeMs, PRECISION_MS, eventManager.getElapsedTimeMs(taskStartedEvent), "Start of task " + i);
				TestUtils.assertTimeBounds(expectedEndTimeMs, PRECISION_MS, eventManager.getElapsedTimeMs(taskTerminatedExceptionallyEvent), "Completion of task " + i);
			}
		}

		if (caughtException) {
			// rewind transaction
			TRANSACTION.undo(fileSystem);
			Assertions.assertTrue(fileSystem.getFiles().isEmpty(), "Undo of transaction failed");
		} else {
			Assertions.assertEquals(actions.size(), fileSystem.getFiles().size(), "Wrong number of files in file system");
		}
	}

	static Object getFileSystemAvailabilityTimeValues() {
		/*
		 * Ensure that these values are not close to expected end times of tasks because the actual schedule will
		 * slightly deviate from the ideal schedule.
		 */
		return ImmutableList.of(300, 1200, 1600, 2400, 3000, 4000);
	}

	private static class Transaction
	{
		private final List<CreateFileAction>	actions;

		Transaction(CreateFileAction... actions) {
			this.actions = ImmutableList.copyOf(actions);
		}

		List<CreateFileAction> getActions() {
			return actions;
		}

		void undo(FileSystem fileSystem) throws IOException {
			for (CreateFileAction action : actions) {
				action.undo(fileSystem);
			}
		}
	}

	private static class CreateFileAction
	{
		private final String	fileName;
		private final long		requiredTimeMs;

		private boolean			triedToPerform;
		private boolean			performed;

		CreateFileAction(String fileName, long requiredTimeMs) {
			this.fileName = fileName;
			this.requiredTimeMs = requiredTimeMs;
		}

		String getFileName() {
			return fileName;
		}

		long getRequiredTimeMs() {
			return requiredTimeMs;
		}

		void apply(FileSystem fileSystem) throws IOException {
			triedToPerform = true;
			fileSystem.createFile(fileName, requiredTimeMs);
			performed = true;
		}

		void undo(FileSystem fileSystem) throws IOException {
			if (performed) {
				fileSystem.removeFile(fileName);
				performed = false;
			}
		}

		boolean hasPerformed() {
			return performed;
		}

		boolean hasTriedToPerform() {
			return triedToPerform;
		}
	}

	private static class FileSystem
	{
		private final long			timeOfShutdownMs;
		private final Set<String>	files				= new HashSet<>();

		private FileSystem(long fileSystemAvailabilityTimeMs) {
			this.timeOfShutdownMs = System.currentTimeMillis() + fileSystemAvailabilityTimeMs;
		}

		void createFile(String fileName, long accessTimeMs) throws IOException {
			accessFileSystem();
			TestUtils.simulateWork(accessTimeMs);
			accessFileSystem();
			synchronized (files) {
				files.add(fileName);
			}
		}

		private void accessFileSystem() throws IOException {
			if (System.currentTimeMillis() >= timeOfShutdownMs) {
				throw new IOException("Could not write file");
			}
		}

		void removeFile(String fileName) throws IOException {
			if (!files.contains(fileName)) {
				throw new IOException("Cannot remove file '" + fileName + "'");
			}
			files.remove(fileName);
		}

		Set<String> getFiles() {
			return files;
		}
	}
}
