package dd.kms.hippodamus.execution;

import java.util.HashMap;
import java.util.Map;

class ObjectCounter<T>
{
	private final Map<T, Long> counters	= new HashMap<>();

	/**
	 * Increments the counter of the specified object and returns its new value
	 */
	long incrementCounter(T object) {
		long newCounter = getCounter(object) + 1;
		counters.put(object, newCounter);
		return newCounter;
	}

	/**
	 * Decrements the counter of the specified object and returns its new value
	 */
	long decrementCounter(T object) {
		long newCounter = getCounter(object) - 1;
		if (newCounter < 0) {
			throw new IllegalStateException("Trying to decrement counter of object '" + object + "' more often than it has been incremented");
		}
		counters.put(object, newCounter);
		return newCounter;
	}

	/**
	 * Returns the counter for the specified object.
	 */
	long getCounter(T object) {
		Long counter = counters.get(object);
		return counter == null ? 0 : counter;
	}
}
