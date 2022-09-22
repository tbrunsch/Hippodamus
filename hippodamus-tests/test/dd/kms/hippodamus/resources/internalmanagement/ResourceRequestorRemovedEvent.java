package dd.kms.hippodamus.resources.internalmanagement;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.testUtils.events.TestEvent;

import java.util.Objects;

/**
 * This event is fired when a resource requestor is removed from the resource.
 */
class ResourceRequestorRemovedEvent extends TestEvent
{
	private final Handle handle;

	ResourceRequestorRemovedEvent(Handle handle) {
		this.handle = handle;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ResourceRequestorRemovedEvent that = (ResourceRequestorRemovedEvent) o;
		return Objects.equals(handle, that.handle);
	}

	@Override
	public int hashCode() {
		return Objects.hash(handle);
	}

	@Override
	public String toString() {
		return "Resource requestor of task " + handle + " removed";
	}
}
