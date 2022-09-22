package dd.kms.hippodamus.api.resources;

import dd.kms.hippodamus.api.handles.Handle;

public interface ResourceRequestor
{
	void retryRequest();
	Handle getHandle();
}
