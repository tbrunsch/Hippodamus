package dd.kms.hippodamus.resources.internalmanagement;

import org.junit.jupiter.api.Assertions;

/**
 * Describes a task of the {@link ResourceManagementTest}. It can be assigned one of two independent threads.
 */
class TaskDescription
{
	private final String	name;
	private final long		requiredResourceSize;
	private final int		threadIndex;

	TaskDescription(String name, long requiredResourceSize, int threadIndex) {
		Assertions.assertTrue(0 <= threadIndex && threadIndex <= 1, "Internal error: Only thread indices 0 and 1 are supported");
		this.name = name;
		this.requiredResourceSize = requiredResourceSize;
		this.threadIndex = threadIndex;
	}

	String getName() {
		return name;
	}

	long getRequiredResourceSize() {
		return requiredResourceSize;
	}

	int getThreadIndex() {
		return threadIndex;
	}

	@Override
	public String toString() {
		return name;
	}
}
