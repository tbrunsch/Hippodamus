package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.exceptions.CoordinatorException;
import dd.kms.hippodamus.handles.ResultHandle;

public class DependencyVerificationSample
{
	public static void main(String[] args) {
		ExecutionCoordinatorBuilder<?> builder = Coordinators.configureExecutionCoordinator()
			.verifyDependencies(true);
		try (ExecutionCoordinator coordinator = builder.build()) {
			ResultHandle<Integer> value = coordinator.execute(() -> returnWithDelay(7));

			// we do not specify first task as dependency => CoordinatorException because dependencies are verified
			coordinator.execute(() -> System.out.println(value.get()));
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (CoordinatorException e){
			System.out.println("Oops. Forgot to specify that the second task depends on the first one...");
		}
	}

	private static int returnWithDelay(int value) throws InterruptedException {
		Thread.sleep(500);
		return value;
	}
}
