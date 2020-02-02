package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;

public class TaskTypeSample
{
	public static void main(String[] args) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 1; i <= 10; i++) {
				int count = i;
				coordinator.configure().taskType(TaskType.REGULAR).execute(() -> printWithDelay("Finished regular task " + count));
				coordinator.configure().taskType(TaskType.IO)     .execute(() -> printWithDelay("Finished IO task "      + count));
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void printWithDelay(String s) throws InterruptedException {
		Thread.sleep(500);
		System.out.println(s);
	}
}
