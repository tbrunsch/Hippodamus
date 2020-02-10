package dd.kms.hippodamus.benchmark;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.testUtils.StopWatch;
import dd.kms.hippodamus.testUtils.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * One of the main purposes of {@link ExecutionCoordinator}s is
 * handling exceptions and dependencies. In this test we benchmark the framework against
 * {@link CompletableFuture} in different scenarios, one time with
 * specified dependencies and one time without.
 */
public class DependencyBenchmark
{
	private static final long	REFERENCE_SORT_TIME_MS	= 2000;
	private static final int	RECURSION_THRESHOLD		= 1 << 4;
	private static final int	PARALLELISM				= 1 << 3;	// measures (not exactly the number) how many parallel tasks to create

	private static final long	PRECISION_MS			= 200;
	private static final double	TOLERANCE				= 1.05;

	@Test
	public void testMergeSort() {
		double[] arrayToSort = determineArrayToSort();
		double[] storage = new double[arrayToSort.length];

		System.out.println("Times (ms)\n==========");

		long timeSingleThreadedMs = runGenericMergeSort(arrayToSort, storage, this::mergeSort);
		System.out.println("single-threaded:                  " + timeSingleThreadedMs);

		TestUtils.waitForEmptyCommonForkJoinPool();
		long timeFutureWithoutDependenciesMs = runGenericMergeSort(arrayToSort, storage, this::mergeSortWithCompletableFuturesWithoutDependencies);
		System.out.println("futures without dependencies:     " + timeFutureWithoutDependenciesMs);

		TestUtils.waitForEmptyCommonForkJoinPool();
		long timeFutureWithDependenciesMs = runGenericMergeSort(arrayToSort, storage, this::mergeSortWithCompletableFuturesWithDependencies);
		System.out.println("futures with dependencies:        " + timeFutureWithDependenciesMs);

		TestUtils.waitForEmptyCommonForkJoinPool();
		long timeCoordinatorWithoutDependenciesMs = runGenericMergeSort(arrayToSort, storage, this::mergeSortWithCoordinatorWithoutDependencies);
		System.out.println("coordinator without dependencies: " + timeCoordinatorWithoutDependenciesMs);

		TestUtils.waitForEmptyCommonForkJoinPool();
		long timeCoordinatorWithDependenciesMs = runGenericMergeSort(arrayToSort, storage, this::mergeSortWithCoordinatorWithDependencies);
		System.out.println("coordinator with dependencies:    " + timeCoordinatorWithDependenciesMs);

		long maxAllowedTimeWithoutDependenciesMs = Math.round(TOLERANCE*timeFutureWithoutDependenciesMs + PRECISION_MS);
		TestUtils.assertTimeUpperBound(maxAllowedTimeWithoutDependenciesMs, timeCoordinatorWithoutDependenciesMs, "Coordinator without dependencies");

		long maxAllowedTimeWithDependenciesMs = Math.round(TOLERANCE*timeFutureWithDependenciesMs + PRECISION_MS);
		TestUtils.assertTimeUpperBound(maxAllowedTimeWithDependenciesMs, timeCoordinatorWithDependenciesMs, "Coordinator with dependencies");
	}

	/**
	 * Returns an array for which the time required by a single-threaded merge sort
	 * implementation is in the order of {@link #REFERENCE_SORT_TIME_MS}.
	 */
	private double[] determineArrayToSort() {
		double[] array;
		int size = 1024;
		long sortTimeMs;
		do {
			array = IntStream.range(0, size).mapToDouble(i -> Math.random()).toArray();
			double[] storage = new double[size];
			sortTimeMs = runGenericMergeSort(array, storage, this::mergeSort);
			size *= 2;
		} while (sortTimeMs < REFERENCE_SORT_TIME_MS);
		return array;
	}

	private long runGenericMergeSort(double[] arrayToSort, double[] storage, GenericMergeSort sorter) {
		StopWatch stopWatch = new StopWatch();
		int size = arrayToSort.length;
		double[] sortedArray = sorter.mergeSort(Arrays.copyOf(arrayToSort, size), storage, 0, size);
		long sortTimeMs = stopWatch.getElapsedTimeMs();
		verifySorted(sortedArray);
		return sortTimeMs;
	}

	private double[] mergeSort(double[] arrayToSort, double[] storage, int first, int last) {
		int size = last - first;
		if (size <= RECURSION_THRESHOLD) {
			Arrays.sort(arrayToSort, first, last);
		} else {
			int mid = (first + last) / 2;
			mergeSort(arrayToSort, storage, first, mid);
			mergeSort(arrayToSort, storage, mid, last);
			System.arraycopy(arrayToSort, first, storage, first, size);
			merge(storage, arrayToSort, first, mid, last);
		}
		return arrayToSort;
	}

	private double[] mergeSortWithCompletableFuturesWithoutDependencies(double[] arrayToSort, double[] storage, int first, int last) {
		CompletableFuture<Void> future = doMergeSortWithCompletableFuturesWithoutDependencies(arrayToSort, storage, first, last);
		future.join();
		return arrayToSort;
	}

	private CompletableFuture<Void> doMergeSortWithCompletableFuturesWithoutDependencies(double[] arrayToSort, double[] storage, int first, int last) {
		int size = last - first;
		if (size <= RECURSION_THRESHOLD || size * PARALLELISM < arrayToSort.length) {
			// use single-threaded merge sort for mid-size arrays portions
			return CompletableFuture.runAsync(() -> mergeSort(arrayToSort, storage, first, last));
		} else {
			// only parallelize until certain size
			int mid = (first + last) / 2;
			CompletableFuture<Void> sortLeft = CompletableFuture.runAsync(() -> mergeSortWithCompletableFuturesWithoutDependencies(arrayToSort, storage, first, mid));
			CompletableFuture<Void> sortRight = CompletableFuture.runAsync(() -> mergeSortWithCompletableFuturesWithoutDependencies(arrayToSort, storage, mid, last));
			CompletableFuture<Void> merge = CompletableFuture.runAsync(() -> {
				sortLeft.join();
				sortRight.join();
				System.arraycopy(arrayToSort, first, storage, first, size);
				merge(storage, arrayToSort, first, mid, last);
			});
			return merge;
		}
	}

	private double[] mergeSortWithCompletableFuturesWithDependencies(double[] arrayToSort, double[] storage, int first, int last) {
		CompletableFuture<Void> future = doMergeSortWithCompletableFuturesWithDependencies(arrayToSort, storage, first, last);
		future.join();
		return arrayToSort;
	}

	private CompletableFuture<Void> doMergeSortWithCompletableFuturesWithDependencies(double[] arrayToSort, double[] storage, int first, int last) {
		int size = last - first;
		if (size <= RECURSION_THRESHOLD || size * PARALLELISM < arrayToSort.length) {
			// use single-threaded merge sort for mid-size arrays portions
			return CompletableFuture.runAsync(() -> mergeSort(arrayToSort, storage, first, last));
		} else {
			// only parallelize until certain size
			int mid = (first + last) / 2;
			CompletableFuture<Void> sortLeft = CompletableFuture.runAsync(() -> mergeSortWithCompletableFuturesWithDependencies(arrayToSort, storage, first, mid));
			return CompletableFuture.runAsync(() -> mergeSortWithCompletableFuturesWithDependencies(arrayToSort, storage, mid, last))
				.runAfterBoth(sortLeft, () -> {
					System.arraycopy(arrayToSort, first, storage, first, size);
					merge(storage, arrayToSort, first, mid, last);
			});
		}
	}

	private double[] mergeSortWithCoordinatorWithoutDependencies(double[] arrayToSort, double[] storage, int first, int last) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			mergeSortWithCoordinatorWithoutDependencies(coordinator, arrayToSort, storage, first, last);
		}
		return arrayToSort;
	}

	private Handle mergeSortWithCoordinatorWithoutDependencies(ExecutionCoordinator coordinator, double[] arrayToSort, double[] storage, int first, int last) {
		int size = last - first;
		if (size <= RECURSION_THRESHOLD || size * PARALLELISM < arrayToSort.length) {
			// use single-threaded merge sort for mid-size arrays portions
			return coordinator.execute(() -> mergeSort(arrayToSort, storage, first, last));
		} else {
			// only parallelize until certain size
			int mid = (first + last) / 2;
			Handle sortLeft = mergeSortWithCoordinatorWithoutDependencies(coordinator, arrayToSort, storage, first, mid);
			Handle sortRight = mergeSortWithCoordinatorWithoutDependencies(coordinator, arrayToSort, storage, mid, last);
			return coordinator.execute(() -> {
				sortLeft.join();
				sortRight.join();
				System.arraycopy(arrayToSort, first, storage, first, size);
				merge(storage, arrayToSort, first, mid, last);
			});
		}
	}

	private double[] mergeSortWithCoordinatorWithDependencies(double[] arrayToSort, double[] storage, int first, int last) {
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			mergeSortWithCoordinatorWithDependencies(coordinator, arrayToSort, storage, first, last);
		}
		return arrayToSort;
	}

	private Handle mergeSortWithCoordinatorWithDependencies(ExecutionCoordinator coordinator, double[] arrayToSort, double[] storage, int first, int last) {
		int size = last - first;
		if (size <= RECURSION_THRESHOLD || size * PARALLELISM < arrayToSort.length) {
			// use single-threaded merge sort for mid-size arrays portions
			return coordinator.execute(() -> mergeSort(arrayToSort, storage, first, last));
		} else {
			// only parallelize until certain size
			int mid = (first + last) / 2;
			Handle sortLeft = mergeSortWithCoordinatorWithDependencies(coordinator, arrayToSort, storage, first, mid);
			Handle sortRight = mergeSortWithCoordinatorWithDependencies(coordinator, arrayToSort, storage, mid, last);
			return coordinator.configure().dependencies(sortLeft, sortRight).execute(() -> {
				System.arraycopy(arrayToSort, first, storage, first, size);
				merge(storage, arrayToSort, first, mid, last);
			});
		}
	}

	private void merge(double[] source, double[] target, int first1, int first2, int last2) {
		int last1 = first2;
		int targetIndex = first1;
		int sourceIndex1 = first1;
		int sourceIndex2 = first2;
		while (sourceIndex1 < last1 && sourceIndex2 < last2) {
			target[targetIndex++] = source[sourceIndex1] <= source[sourceIndex2] ? source[sourceIndex1++] : source[sourceIndex2++];
		}
		while (sourceIndex1 < last1) {
			target[targetIndex++] = source[sourceIndex1++];
		}
		while (sourceIndex2 < last2) {
			target[targetIndex++] = source[sourceIndex2++];
		}
	}

	private void verifySorted(double[] array) {
		for (int i = 1; i < array.length; i++) {
			Assert.assertTrue("The array is not sorted", array[i-1] <= array[i]);
		}
	}

	@FunctionalInterface
	private interface GenericMergeSort
	{
		double[] mergeSort(double[] arrayToSort, double[] storage, int first, int last);
	}
}
