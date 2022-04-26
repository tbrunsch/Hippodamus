package dd.kms.hippodamus;

import com.google.common.collect.ImmutableList;
import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This test focuses on the following scenario: We have a transaction consisting
 * of multiple parallel tasks that all have to complete. If any of these tasks
 * fails, then no further task should be started and the transaction should be
 * rewound.
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
	private static final int[]	START_TIMES	= { 0, 0, 500, 800, 1500, 1800, 2100, 2500, 3100 };

	static Object getParameters() {
		return ImmutableList.of(300, 1200, 1600, 2400, 3000);
	}

	@ParameterizedTest(name = "transaction timeout after {0} ms")
	@MethodSource("getParameters")
	void testCopyFiles(long fileSystemAvailabilityTimeMs) throws IOException {
		/*
		 * The file system will "shut down" after fileSystemAvailabilityTimeMs millis.
		 * => Trying to create a file afterwards will result in an IOException.
		 */
		FileSystem fileSystem = new FileSystem(System.currentTimeMillis() + fileSystemAvailabilityTimeMs);
		ExecutionCoordinatorBuilder coordinatorBuilder = Coordinators.configureExecutionCoordinator()
			.executorService(TaskType.BLOCKING, Executors.newFixedThreadPool(2), true);
		boolean caughtException = false;
		List<CreateFileAction> actions = TRANSACTION.getActions();
		try (ExecutionCoordinator coordinator = coordinatorBuilder.build()) {
			for (CreateFileAction action : actions) {
				coordinator.configure().taskType(TaskType.BLOCKING).execute(() -> action.apply(fileSystem));
			}
		} catch (IOException e) {
			caughtException = true;

			// Check that only the files of those actions exist that have been executed before the file system shutdown
			Set<String> expectedExistingFiles = new HashSet<>();
			for (int i = 0; i < actions.size(); i++) {
				CreateFileAction action = actions.get(i);
				boolean fileExpectedToExist = START_TIMES[i] < fileSystemAvailabilityTimeMs;
				if (fileExpectedToExist) {
					Assertions.assertTrue(action.hasTriedToPerform(), "The action for file name '" + action.getFileName() + "' should have been performed");
					expectedExistingFiles.add(action.getFileName());
				} else {
					Assertions.assertFalse(action.hasTriedToPerform(), "The coordinator should have stopped the task containing that action for file name '" + action.getFileName() + "' before executing it");
				}
			}
			Assertions.assertEquals(expectedExistingFiles, fileSystem.getFiles(), "There exist other files than expected");

			// Undo transaction
			TRANSACTION.undo(fileSystem);
			Assertions.assertTrue(fileSystem.getFiles().isEmpty(), "Undo of transaction failed");
		}
		Assertions.assertTrue(caughtException, "An exception has been swallowed");
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

		void apply(FileSystem fileSystem) throws IOException {
			triedToPerform = true;
			fileSystem.createFile(fileName);
			performed = true;
			TestUtils.simulateWork(requiredTimeMs);
			fileSystem.accessFileSystem();
		}

		void undo(FileSystem fileSystem) throws IOException {
			if (performed) {
				fileSystem.removeFile(fileName);
				performed = false;
			}
		}

		boolean hasTriedToPerform() {
			return triedToPerform;
		}
	}

	private static class FileSystem
	{
		private final long			timeOfShutdownMs;
		private final Set<String>	files				= new HashSet<>();

		private FileSystem(long timeOfShutdownMs) {
			this.timeOfShutdownMs = timeOfShutdownMs;
		}

		synchronized void createFile(String fileName) throws IOException {
			accessFileSystem();
			files.add(fileName);
		}

		void accessFileSystem() throws IOException {
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
