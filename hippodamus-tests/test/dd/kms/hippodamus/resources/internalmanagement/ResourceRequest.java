package dd.kms.hippodamus.resources.internalmanagement;

import dd.kms.hippodamus.api.resources.ResourceRequestor;

/**
 * This class is used by {@link TestResource} to manage postponed resource requests.
 */
class ResourceRequest
{
	private final TaskDescription	resourceShare;
	private final ResourceRequestor resourceRequestor;

	ResourceRequest(TaskDescription resourceShare, ResourceRequestor resourceRequestor) {
		this.resourceShare = resourceShare;
		this.resourceRequestor = resourceRequestor;
	}

	TaskDescription getResourceShare() {
		return resourceShare;
	}

	ResourceRequestor getResourceRequestor() {
		return resourceRequestor;
	}
}
