package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.execution.ExecutionController;

/**
 * This interface can be used to model countable resources, i.e., resources that can be divided into pieces whose sizes
 * can be described by an integral value.
 */
public interface CountableResource
{
	ExecutionController getShare(long size);
}
