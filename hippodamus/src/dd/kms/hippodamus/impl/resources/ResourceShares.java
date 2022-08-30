package dd.kms.hippodamus.impl.resources;

import dd.kms.hippodamus.api.resources.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ResourceShares
{
	public static <T> ResourceShare wrapResourceShare(Resource<T> resource, Supplier<T> resourceShareSupplier) {
		return new WrappedResourceShare<>(resource, resourceShareSupplier);
	}

	public static ResourceShare createCompoundResourceShare(List<ResourceShare> resourceShares) {
		// ensure to sort the resource shares to avoid deadlocks
		if (resourceShares.size() <= 1) {
			return new CompoundResourceShare(resourceShares);
		}
		List<WrappedResourceShare<?>> sortedResourcesShares = new ArrayList<>(resourceShares.size());
		for (ResourceShare resourceShare : resourceShares) {
			if (!(resourceShare instanceof WrappedResourceShare)) {
				throw new IllegalStateException("Resource share " + resourceShare + " is not " + WrappedResourceShare.class);
			}
			sortedResourcesShares.add((WrappedResourceShare<?>) resourceShare);
		}
		Collections.sort(sortedResourcesShares);
		return new CompoundResourceShare(sortedResourcesShares);
	}
}
