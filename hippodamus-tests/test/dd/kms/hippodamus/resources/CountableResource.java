package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.execution.ExecutionController;

public interface CountableResource
{
	ExecutionController getShare(long size);
}
