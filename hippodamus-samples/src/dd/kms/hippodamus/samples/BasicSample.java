package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;

public class BasicSample
{
	public static void main(String[] args) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> System.out.println("Hello "));
			coordinator.execute(() -> System.out.println("World!"));
		}
	}
}
