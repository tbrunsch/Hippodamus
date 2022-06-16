package dd.kms.hippodamus.testUtils.events;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.states.CoordinatorState;
import dd.kms.hippodamus.testUtils.states.HandleState;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestEventManager
{
	private final long							initialTimestamp	= System.currentTimeMillis();
	private final Map<TestEvent, Long>			eventTimesMs		= new LinkedHashMap<>();
	private final Map<Handle, Throwable>		taskExceptions		= new HashMap<>();
	private final Multimap<TestEvent, Runnable> eventListeners		= ArrayListMultimap.create();

	public synchronized void fireEvent(TestEvent event) {
		if (eventTimesMs.containsKey(event)) {
			throw new IllegalStateException("Encountered event '" + event + "' twice");
		}
		eventTimesMs.put(event, System.currentTimeMillis() - initialTimestamp);
		Collection<Runnable> listeners = eventListeners.get(event);
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	public void onHandleEvent(Handle handle, HandleState state, Runnable listener) {
		HandleEvent e = new HandleEvent(handle, state);
		onEvent(e, listener);
	}

	public void onCoordinatorEvent(CoordinatorState state, Runnable listener) {
		CoordinatorEvent e = new CoordinatorEvent(state);
		onEvent(e, listener);
	}

	private void onEvent(TestEvent e, Runnable listener) {
		synchronized (this) {
			// call listener once for every matching event that has already been encountered
			for (TestEvent event : eventTimesMs.keySet()) {
				if (event.equals(e)) {
					listener.run();
				}
			}
			eventListeners.put(e, listener);
		}
	}

	public synchronized void setException(Handle handle, Throwable t) {
		taskExceptions.put(handle, t);
	}

	public Throwable getException(Handle handle) {
		return taskExceptions.get(handle);
	}

	public boolean encounteredEvent(Handle handle, HandleState state) {
		return encounteredEvent(new HandleEvent(handle, state));
	}

	public boolean encounteredEvent(TestEvent event) {
		return eventTimesMs.containsKey(event);
	}

	public boolean before(Handle handle1, HandleState state1, Handle handle2, HandleState state2) {
		return before(handle1, state1, new HandleEvent(handle2, state2));
	}

	public boolean before(Handle handle1, HandleState state1, TestEvent event2) {
		return before (new HandleEvent(handle1, state1), event2);
	}

	public boolean before(TestEvent event1, Handle handle2, HandleState state2) {
		return before(event1, new HandleEvent(handle2, state2));
	}

	public boolean before(TestEvent event1, TestEvent event2) {
		return getDurationMs(event1, event2) >= 0;
	}

	public long getDurationMs(Handle handle1, HandleState state1, Handle handle2, HandleState state2) {
		return getDurationMs(handle1, state1, new HandleEvent(handle2, state2));
	}

	public long getDurationMs(Handle handle1, HandleState state1, TestEvent event2) {
		return getDurationMs(new HandleEvent(handle1, state1), event2);
	}

	public long getDurationMs(TestEvent event1, Handle handle2, HandleState state2) {
		return getDurationMs(event1, new HandleEvent(handle2, state2));
	}

	public long getDurationMs(TestEvent event1, TestEvent event2) {
		long timestamp1 = getElapsedTimeMs(event1);
		long timestamp2 = getElapsedTimeMs(event2);
		return timestamp2 - timestamp1;
	}

	public long getElapsedTimeMs() {
		return System.currentTimeMillis() - initialTimestamp;
	}

	public long getElapsedTimeMs(Handle handle, HandleState state) {
		return getElapsedTimeMs(new HandleEvent(handle, state));
	}

	public long getElapsedTimeMs(TestEvent event) {
		Long timestamp = eventTimesMs.get(event);
		if (timestamp == null) {
			throw new IllegalArgumentException("Event '" + event + "' has not been encountered");
		}
		return timestamp;
	}
}
