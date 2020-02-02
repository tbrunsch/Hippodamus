package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;

public class InfiniteLoopSample
{
	public static void main(String[] args) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> { throw new Exception("Break infinite loop!"); });
			while (true);
		} catch (Exception e) {
			/*
			 * The exception will be thrown in the task's thread and delegated to the coordinator,
			 * but the coordinator is not given a chance to rethrow it in its own thread.
			 *
			 * If you really must write such code, then you have to regularly call coordinator.checkException()
			 * within the infinite loop.
			 */
			System.out.println("Reached unreachable code!");
		}
	}
}
