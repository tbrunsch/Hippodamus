package dd.kms.hippodamus.samples;

import dd.kms.hippodamus.aggregation.Aggregator;
import dd.kms.hippodamus.aggregation.Aggregators;
import dd.kms.hippodamus.coordinator.AggregationCoordinator;
import dd.kms.hippodamus.coordinator.Coordinators;

public class BooleanAggregationSample
{
	public static void main(String[] args) {
		Aggregator<Boolean, Boolean> successAggregator = Aggregators.conjunction();
		try (AggregationCoordinator<Boolean, Boolean> coordinator = Coordinators.createAggregationCoordinator(successAggregator)) {
			for (int i = 0; i < 10; i++) {
				coordinator.aggregate(() -> runTask());
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("\nSuper task successful: " + successAggregator.getAggregatedValue());
	}

	private static boolean runTask() throws InterruptedException {
		Thread.sleep(500);
		boolean subTaskSuccess = Math.random() >= 0.1;
		System.out.println("Sub task successful: " + subTaskSuccess);
		return subTaskSuccess;
	}
}
