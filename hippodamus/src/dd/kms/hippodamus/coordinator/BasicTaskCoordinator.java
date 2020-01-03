package dd.kms.hippodamus.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import dd.kms.hippodamus.exceptions.*;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.handles.impl.DefaultResultHandle;
import dd.kms.hippodamus.handles.impl.StoppedResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.*;
import java.util.concurrent.ExecutorService;

class BasicTaskCoordinator implements TaskCoordinator
{
	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersById;

	private final HandleDependencyManager				handleDependencyManager;
	private final Map<Integer, String>					handleNamesByHashCode			= new HashMap<>();

	/**
	 * The field may be set from a different thread than the one the coordinator is running in.
	 * However, the coordinator will regularly check it
	 * <ul>
	 *     <li>whenever a new task is added or</li>
	 *     <li>when the coordinator is closing</li>
	 * </ul>
	 */
	private Throwable						exception;

	BasicTaskCoordinator() {
		this(ImmutableMap.<Integer, ExecutorServiceWrapper>builder()
				.put(ExecutorServiceIds.REGULAR,	ExecutorServiceWrapper.COMMON_FORK_JOIN_POOL_WRAPPER)
				.put(ExecutorServiceIds.IO,			ExecutorServiceWrapper.create(1))
				.build());
	}

	BasicTaskCoordinator(Map<Integer, ExecutorServiceWrapper> executorServiceWrappersById) {
		this.executorServiceWrappersById = ImmutableMap.copyOf(executorServiceWrappersById);
		this.handleDependencyManager = new HandleDependencyManager(this);
	}

	@Override
	public <E extends Exception> Handle execute(ExceptionalRunnable<E> runnable, int executorServiceId, Handle... dependencies) throws E {
		return execute(Exceptions.asStoppable(runnable), executorServiceId, dependencies);
	}

	@Override
	public <E extends Exception> Handle execute(StoppableExceptionalRunnable<E> runnable, int executorServiceId, Handle... dependencies) throws E {
		return execute(Exceptions.asCallable(runnable), executorServiceId, dependencies);
	}

	@Override
	public final <V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, int executorServiceId, Handle... dependencies) throws E {
		return execute(Exceptions.asStoppable(callable), executorServiceId, Optional.empty(), dependencies);
	}

	@Override
	public final <V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable, int executorServiceId, Handle... dependencies) throws E {
		return execute(callable, executorServiceId, Optional.empty(), dependencies);
	}

	// TODO: Offer option to run with name
	private <V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable, int executorServiceId, Optional<String> optName, Handle... dependencies) throws E {
		ExecutorServiceWrapper executorServiceWrapper = executorServiceWrappersById.get(executorServiceId);
		Preconditions.checkNotNull(executorServiceWrapper, "Unknown executor service ID " + executorServiceId + ". Use ExecutorServiceIds.REGULAR or ExecutorServiceIds.IO or a custom ID you have registered an executor service for.");
		ExecutorService executorService = executorServiceWrapper.getExecutorService();
		synchronized (this) {
			String name = optName.isPresent() ? optName.get() : createGenericTaskName();
			checkException();
			boolean dependencyStopped = Arrays.stream(dependencies).anyMatch(Handle::hasStopped);
			final ResultHandle<V> resultHandle;
			if (dependencyStopped) {
				resultHandle = createStoppedHandle();
				registerHandleName(resultHandle, name);
			} else {
				resultHandle = new DefaultResultHandle<>(this, executorService, callable);
				registerHandleName(resultHandle, name);
				resultHandle.onCompletion(() -> onCompletion(resultHandle));
				resultHandle.onException(e -> onException(resultHandle, e));
				handleDependencyManager.addDependencies(resultHandle, dependencies);
				boolean allDependenciesCompleted = Arrays.stream(dependencies).allMatch(Handle::hasCompleted);
				if (allDependenciesCompleted) {
					resultHandle.submit();
				}
			}
			return resultHandle;
		}
	}

	private void registerHandleName(Handle handle, String name) {
		int hashCode = System.identityHashCode(handle);
		handleNamesByHashCode.put(hashCode, name);
	}

	private String getHandleName(Handle handle) {
		int hashCode = System.identityHashCode(handle);
		String name = handleNamesByHashCode.get(hashCode);
		return name == null ? "Unknown handle " + hashCode : name;
	}

	private String createGenericTaskName() {
		return "Task " + (handleDependencyManager.getManagedHandles().size() + 1);
	}

	<T> ResultHandle<T> createStoppedHandle() {
		return new StoppedResultHandle<>(this);
	}

	void checkException() {
		if (exception != null) {
			Exceptions.throwUnchecked(exception);
		}
	}

	private void onException(Handle handle, Throwable exception) {
		synchronized (this) {
			if (this.exception == null) {
				this.exception = exception;
				stop();
			}
		}
	}

	private void onCompletion(Handle handle) {
		synchronized (this) {
			List<Handle> executableHandles = handleDependencyManager.getExecutableHandles(handle);
			executableHandles.forEach(Handle::submit);
		}
	}

	@Override
	public void stop() {
		synchronized (this) {
			Collection<Handle> managedHandles = handleDependencyManager.getManagedHandles();
			managedHandles.forEach(Handle::stop);
		}
	}

	@Override
	public void stopDependentHandles(Handle handle) {
		synchronized (this) {
			List<Handle> dependentHandles = handleDependencyManager.getDependentHandles(handle);
			dependentHandles.forEach(Handle::stop);
		}
	}

	@Override
	public void log(LogLevel logLevel, Handle handle, String message) {
		// TODO
		if (logLevel == LogLevel.DEBUGGING) {
			return;
		}
		synchronized (this) {
			String name = getHandleName(handle);
			System.out.println(name + ": " + message);
			if (logLevel == LogLevel.INTERNAL_ERROR) {
				throw new IllegalStateException(message);
			}
		}
	}

	@Override
	public void close() throws InterruptedException {
		try {
			Collection<Handle> managedHandles = handleDependencyManager.getManagedHandles();
			for (Handle handle : managedHandles) {
				while (!handle.hasCompleted() && !handle.hasStopped()) {
					checkException();
				}
			}
			checkException();
		} finally {
			Throwable throwable = null;
			for (ExecutorServiceWrapper executorServiceWrapper : executorServiceWrappersById.values()) {
				try {
					executorServiceWrapper.close();
				} catch (Throwable t) {
					if (throwable == null) {
						throwable = t;
					}
				}
			}
			if (throwable != null) {
				throw new IllegalStateException("Exception when closing executor services: " + throwable.getMessage(), throwable);
			}
		}
	}
}
