package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.resources.Resource;
import dd.kms.hippodamus.api.resources.ResourceRequestor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test checks that exceptions that are thrown by {@link Resource} implementations are not swallowed, but handled
 * by the {@link ExecutionCoordinator}.
 */
class ExceptionalResourceTest
{
	@ParameterizedTest(name = "time of exception: {0}")
	@MethodSource("getResourceMethods")
	void testResourceWithException(ResourceMethod timeOfException) {
		ExceptionalResource resource = new ExceptionalResource(timeOfException);
		boolean caughtCoordinatorException = false;
		try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
			coordinator.configure()
				.requiredResource(resource, () -> null)
				.execute(() -> {});
		} catch (CoordinatorException e) {
			caughtCoordinatorException = true;
		}
		Assertions.assertTrue(caughtCoordinatorException, "A CoordinatorException should have been thrown");
		Assertions.assertFalse(resource.removeHasBeenCalled(), "This method should not be called in this unit test");
	}

	private static Object[] getResourceMethods() {
		return ResourceMethod.values();
	}

	private static class ExceptionalResource implements Resource<Object>
	{
		private final ResourceMethod	timeOfException;
		private boolean 				calledRemove;

		ExceptionalResource(ResourceMethod timeOfException) {
			this.timeOfException = timeOfException;
		}

		@Override
		public void addPendingResourceShare(Object resourceShare) {
			possiblyThrowException(ResourceMethod.ADD_PENDING_RESOURCE_SHARE);
		}

		@Override
		public void removePendingResourceShare(Object resourceShare) {
			possiblyThrowException(ResourceMethod.REMOVE_PENDING_RESOURCE_SHARE);
		}

		@Override
		public boolean tryAcquire(Object resourceShare, ResourceRequestor resourceRequestor) {
			possiblyThrowException(ResourceMethod.TRY_ACQUIRE);
			return true;
		}

		@Override
		public void release(Object resourceShare) {
			possiblyThrowException(ResourceMethod.RELEASE);
		}

		@Override
		public void remove(ResourceRequestor resourceRequestor) {
			calledRemove = true;
		}

		boolean removeHasBeenCalled() {
			return calledRemove;
		}

		private void possiblyThrowException(ResourceMethod resourceMethod) {
			if (resourceMethod == timeOfException) {
				throw new ResourceException();
			}
		}
	}

	private enum ResourceMethod
	{
		ADD_PENDING_RESOURCE_SHARE,
		REMOVE_PENDING_RESOURCE_SHARE,
		TRY_ACQUIRE,
		RELEASE
	}

	private static class ResourceException extends RuntimeException {}
}
