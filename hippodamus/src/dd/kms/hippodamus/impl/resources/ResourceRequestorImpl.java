package dd.kms.hippodamus.impl.resources;

import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.resources.ResourceRequestor;
import dd.kms.hippodamus.impl.handles.HandleImpl;

import java.util.concurrent.CompletableFuture;

public class ResourceRequestorImpl implements ResourceRequestor
{
	private final HandleImpl	handle;

	public ResourceRequestorImpl(HandleImpl handle) {
		this.handle = handle;
	}

	@Override
	public void retryRequest() {
		/*
		 * Calls handle.submit() asynchronously. This is required to avoid deadlocks when a resource
		 * calls this method (see TECHDOC for details).
		 */
		CompletableFuture.runAsync(handle::submit);
	}

	@Override
	public Handle getHandle() {
		return handle;
	}
}
