package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.handles.ResultHandle;

public class DependencySample
{
	public static void main(String[] args) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			ResultHandle<Integer> value1 = coordinator.execute(() -> returnWithDelay(5));
			ResultHandle<Integer> value2 = coordinator.execute(() -> returnWithDelay(7));

			// without specifying dependencies
			coordinator.execute(() -> System.out.println(value1.get() + value2.get()));

			// with specifying dependencies
			coordinator.configure().dependencies(value1, value2).execute(() -> System.out.println(value1.get() + value2.get()));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static int returnWithDelay(int value) throws InterruptedException {
		Thread.sleep(500);
		return value;
	}
}
