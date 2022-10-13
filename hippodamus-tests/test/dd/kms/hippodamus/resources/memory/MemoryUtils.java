package dd.kms.hippodamus.resources.memory;

class MemoryUtils
{
	private static final String[]	MEMORY_UNITS	= { "Bytes", "kB", "MB", "GB", "TB" };

	/**
	 * @param safetyFactor value between 0 and 1. Since it seems to be impossible to determine the available memory,
	 *                     a (theoretically possible) upper bound of the available memory is computed and multiplied
	 *                     by this safety factor. The lower the factor, the more likely it is that the returned amount
	 *                     of memory can really be allocated.
	 * @return an estimation of the available memory.
	 */
	static long estimateAvailableMemory(double safetyFactor) {
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		if (maxMemory == Long.MAX_VALUE) {
			throw new IllegalStateException("Cannot run OutOfMemory tests because no max heap size is defined");
		}
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long allocatedMemory = totalMemory - freeMemory;
		long estimatedUsableMemory = Math.max((long) (safetyFactor * maxMemory), totalMemory);
		return estimatedUsableMemory - allocatedMemory;
	}

	static String formatMemory(long sizeInBytes) {
		double size = sizeInBytes;
		for (int i = 0; ; i++) {
			if (size < 1024 || i == MEMORY_UNITS.length - 1) {
				return String.format("%.2f %s", size, MEMORY_UNITS[i]);
			}
			size /= 1024;
		}
	}

	static void forceGc() {
		for (int i = 0; i < 3; i++) {
			System.gc();
		}
	}
}
