package dd.kms.hippodamus.testUtils.events;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.states.HandleState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TestEventManager
{
	private final List<TestEvent>				events			= new ArrayList<>();
	private final Multimap<TestEvent, Runnable> eventListeners	= ArrayListMultimap.create();

	public synchronized void encounteredEvent(TestEvent event) {
		events.add(event);
		Collection<Runnable> listeners = eventListeners.get(event);
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	public void onHandleEvent(Handle handle, HandleState state, Runnable listener) {
		HandleEvent e = new HandleEvent(handle, state, null);
		synchronized (this) {
			// call listener once for every matching event that has already been encountered
			for (TestEvent event : events) {
				if (event.equals(e)) {
					listener.run();
				}
			}
			eventListeners.put(e, listener);
		}
	}

	public boolean before(Handle handle1, HandleState state1, Handle handle2, HandleState state2) {
		return before(handle1, state1, new HandleEvent(handle2, state2, null));
	}

	public boolean before(Handle handle1, HandleState state1, TestEvent event2) {
		return before (new HandleEvent(handle1, state1, null), event2);
	}

	public boolean before(TestEvent event1, Handle handle2, HandleState state2) {
		return before(event1, new HandleEvent(handle2, state2, null));
	}

	public boolean before(TestEvent event1, TestEvent event2) {
		int index1 = events.indexOf(event1);
		if (index1 < 0) {
			throw new IllegalArgumentException("First event has not been encountered");
		}
		int index2 = events.indexOf(event2);
		if (index2 < 0) {
			throw new IllegalArgumentException("Second event has not been encountered");
		}
		return index1 < index2 && events.get(index1).getTimestamp() <= events.get(index2).getTimestamp();
	}
}
