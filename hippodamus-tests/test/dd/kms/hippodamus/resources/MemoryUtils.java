package dd.kms.hippodamus.resources;

class MemoryUtils
{
	private static final String[]	MEMORY_UNITS	= { "Bytes", "kB", "MB", "GB", "TB" };

	static long getAvailableMemory() {
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		if (maxMemory == Long.MAX_VALUE) {
			throw new IllegalStateException("Cannot run OutOfMemory tests because no max heap size is defined");
		}
		long allocatedMemory = runtime.totalMemory() - runtime.freeMemory();
		return maxMemory - allocatedMemory;
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
