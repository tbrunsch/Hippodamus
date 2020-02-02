package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;

public class IntractableExceptionSample
{
	public static void main(String[] args) {
		System.out.println("Ignoring intractable exceptions...");
		ignoreIntractableExceptions();

		System.out.println("Considering intractable exceptions...");
		considerIntractableExceptions();
	}

	private static void ignoreIntractableExceptions() {
		String s = 42 % 7 == 0 ? null : "Never used";
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.execute(() -> Thread.sleep(2000));
			System.out.println(s.length());	// NPE
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	private static void considerIntractableExceptions() {
		String s = 42 % 7 == 0 ? null : "Never used";
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.permitTaskSubmission(false);
			coordinator.execute(() -> Thread.sleep(2000));
			System.out.println(s.length());	// NPE
			coordinator.permitTaskSubmission(true);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}
}
