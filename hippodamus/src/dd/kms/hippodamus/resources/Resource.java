package dd.kms.hippodamus.resources;

public interface Resource<S>
{
	/**
	 * Tries to acquire a share of the resource. Depending on the kind of resource,
	 * a share can be an amount of memory in bytes, a file in  a file system etc.
	 * The resource might reject this request, but it should be accepted whenever
	 * possible because Hippodamus will throw an {@link IllegalStateException} when
	 * the request is rejected and no other managed task holds a share of that
	 * resource.
	 * @param share The share of the resource to acquire
	 * @return true if acquiring the share of the resource succeeded
	 */
	boolean acquire(S share);

	/**
	 * Releases the share of the resource that has earlier been acquired by {@link #acquire(Object, boolean)}.
	 * @param share The share of the resource to release
	 */
	void release(S share);
}
