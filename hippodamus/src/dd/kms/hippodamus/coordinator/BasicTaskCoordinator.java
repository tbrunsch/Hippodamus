package dd.kms.hippodamus.coordinator;

import dd.kms.hippodamus.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.exceptions.Exceptions;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.handles.impl.DefaultResultHandle;
import dd.kms.hippodamus.handles.impl.StoppedResultHandle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.*;
import java.util.concurrent.ExecutorService;

class BasicTaskCoordinator implements TaskCoordinator
{
	private final ExecutorServiceWrapper	regularTaskExecutorServiceWrapper;
	private final ExecutorServiceWrapper	ioTaskExecutorServiceWrapper;

	private final HandleDependencyManager	handleDependencyManager;
	private final Map<Integer, String>		handleNamesByHashCode				= new HashMap<>();

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
		this(ExecutorServiceWrapper.COMMON_FORK_JOIN_POOL_WRAPPER, ExecutorServiceWrapper.create(1));
	}

	BasicTaskCoordinator(ExecutorServiceWrapper regularTaskExecutorServiceWrapper, ExecutorServiceWrapper ioTaskExecutorServiceWrapper) {
		this.regularTaskExecutorServiceWrapper = regularTaskExecutorServiceWrapper;
		this.ioTaskExecutorServiceWrapper = ioTaskExecutorServiceWrapper;
		this.handleDependencyManager = new HandleDependencyManager(this);
	}

	@Override
	public <E extends Exception> Handle execute(ExceptionalRunnable<E> runnable, Handle... dependencies) throws E {
		return execute(Exceptions.asCallable(runnable), dependencies);
	}

	@Override
	public <E extends Exception> Handle executeIO(ExceptionalRunnable<E> runnable, Handle... dependencies) throws E {
		return executeIO(Exceptions.asCallable(runnable), dependencies);
	}

	@Override
	public final <V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, Handle... dependencies) throws E {
		return execute(callable, regularTaskExecutorServiceWrapper.getExecutorService(), dependencies);
	}

	@Override
	public final <V, E extends Exception> ResultHandle<V> executeIO(ExceptionalCallable<V, E> callable, Handle... dependencies) throws E {
		return execute(callable, ioTaskExecutorServiceWrapper.getExecutorService(), dependencies);
	}

	@Override
	public <E extends Exception> Handle execute(ExceptionalRunnable<E> runnable, ExecutorService executorService, Handle... dependencies) throws E {
		return execute(Exceptions.asCallable(runnable), executorService, dependencies);
	}

	@Override
	public final <V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, ExecutorService executorService, Handle... dependencies) throws E {
		return execute(callable, executorService, Optional.empty(), dependencies);
	}

	// TODO: Offer option to run with name
	private <V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, ExecutorService executorService, Optional<String> optName, Handle... dependencies) throws E {
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
			regularTaskExecutorServiceWrapper.close();
			ioTaskExecutorServiceWrapper.close();
		}
	}
}
