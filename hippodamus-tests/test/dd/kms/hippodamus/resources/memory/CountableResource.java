package dd.kms.hippodamus.resources.memory;

import dd.kms.hippodamus.api.resources.Resource;

/**
 * This interface can be used to model countable resources, i.e., resources that can be divided into pieces whose sizes
 * can be described by an integral value.
 */
public interface CountableResource extends Resource<Long>
{
}
