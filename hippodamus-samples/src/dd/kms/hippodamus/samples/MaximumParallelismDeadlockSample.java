package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.handles.ResultHandle;

import java.util.concurrent.ExecutorService;

/**
 * This sample demonstrates how limiting the maximum parallelism can cause deadlocks if
 * dependencies are not correctly. The sample limits the maximum parallelism to 2 and used
 * 4 tasks with the dependency chain task 1 <- task 2 <- task 3 <- task 4. However, the
 * coordinator is not informed about the dependencies task 2 <- task 3 and task 3 <- task 4.<br/>
 * <br/>
 * The program flow will be as follows:
 * <ol>
 *     <li>
 *         The coordinator submits task 1 because it has no dependencies.
 *     </li>
 *     <li>
 *         The coordinator <b>does not submit</b> task 2 because it specifies to depend on task 1
 *         and task 1 has not yet finished.
 *     </li>
 *     <li>
 *         The coordinator submits task 3 because it does not specify any dependency.
 *     </li>
 *     <li>
 *         The coordinator tries to submit task 4 because it does not specify any dependency.
 *         Since the {@link ExecutorService} is already processing 2 tasks (task 1 and task 3),
 *         task 4 is queued for later submission.
 *     </li>
 *     <li>
 *         Task 1 terminates, which causes the queued task 4 to be submitted and task 2 to be
 *         queued for later submission.
 *     </li>
 *     <li>
 *         Task 3 keeps waiting for task 2 to terminate, while task 4 keeps waiting for task 3
 *         to terminate. However, task 2 will never be submitted because of the maximum parallelism.
 *     </li>
 * </ol>
 */
public class MaximumParallelismDeadlockSample
{
	public static void main(String[] args) {
		ExecutionCoordinatorBuilder<?> builder = Coordinators.configureExecutionCoordinator()
			.maximumParallelism(TaskType.REGULAR, 2)
			.logger(((logLevel, taskName, message) -> System.out.println(taskName + ": " + message)));
		try (ExecutionCoordinator coordinator = builder.build()) {
			ResultHandle<Integer> task1 = coordinator.execute(() -> returnWithDelay(1));
			ResultHandle<Integer> task2 = coordinator.configure().dependencies(task1).execute(() -> returnWithDelay(task1.get() + 1));
			ResultHandle<Integer> task3 = coordinator.execute(() -> returnWithDelay(task2.get() + 1));
			ResultHandle<Integer> task4 = coordinator.execute(() -> returnWithDelay(task3.get() + 1));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static int returnWithDelay(int value) throws InterruptedException {
		Thread.sleep(500);
		return value;
	}
}
