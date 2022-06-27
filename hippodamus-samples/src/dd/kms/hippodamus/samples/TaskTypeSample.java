package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;

public class TaskTypeSample
{
	public static void main(String[] args) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			for (int i = 1; i <= 10; i++) {
				int count = i;
				coordinator.configure().taskType(TaskType.COMPUTATIONAL).execute(() -> printWithDelay("Finished computational task " + count));
				coordinator.configure().taskType(TaskType.BLOCKING)     .execute(() -> printWithDelay("Finished blocking task " + count));
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
