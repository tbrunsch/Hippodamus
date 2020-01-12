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

class ExecutionCoordinatorImpl implements ExecutionCoordinator
{
	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;

	private final HandleDependencyManager				handleDependencyManager;
	private final Map<Integer, String>					handleNamesByHashCode			= new HashMap<>();

	private boolean										permitTaskSubmission			= true;

	/**
	 * This field contains all handles that can already be submitted, but whose submission is denied
	 * because {@link #permitTaskSubmission} is {@code false}. These handles will be submitted as
	 * soon as {@code permitTaskSubmission} is set to {@code true}.
	 */
	private final List<Handle>							pendingHandles					= new ArrayList<>();

	/**
	 * The field may be set from a different thread than the one the coordinator is running in.
	 * However, the coordinator will regularly check it
	 * <ul>
	 *     <li>whenever a new task is added or</li>
	 *     <li>when the coordinator is closing</li>
	 * </ul>
	 */
	private Throwable									exception;

	ExecutionCoordinatorImpl() {
		this(ImmutableMap.<Integer, ExecutorServiceWrapper>builder()
				.put(TaskType.REGULAR,	ExecutorServiceWrapper.COMMON_FORK_JOIN_POOL_WRAPPER)
				.put(TaskType.IO,			ExecutorServiceWrapper.create(1))
				.build());
	}

	ExecutionCoordinatorImpl(Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType) {
		this.executorServiceWrappersByTaskType = ImmutableMap.copyOf(executorServiceWrappersByTaskType);
		this.handleDependencyManager = new HandleDependencyManager(this);
	}

	<E extends Exception> Handle execute(ExceptionalRunnable<E> runnable, ExecutionConfiguration configuration) {
		return execute(Exceptions.asStoppable(runnable), configuration);
	}

	<E extends Exception> Handle execute(StoppableExceptionalRunnable<E> runnable, ExecutionConfiguration configuration) {
		return execute(Exceptions.asCallable(runnable), configuration);
	}

	<V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable, ExecutionConfiguration configuration) {
		return execute(Exceptions.asStoppable(callable), configuration);
	}

	<V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable, ExecutionConfiguration configuration) {
		ExecutorServiceWrapper executorServiceWrapper = getExecutorServiceWrapper(configuration);
		ExecutorService executorService = executorServiceWrapper.getExecutorService();
		String name = getTaskName(configuration);
		Collection<Handle> dependencies = configuration.getDependencies();
		synchronized (this) {
			checkException();
			boolean dependencyStopped = dependencies.stream().anyMatch(Handle::hasStopped);
			final ResultHandle<V> resultHandle;
			if (dependencyStopped) {
				resultHandle = createStoppedHandle();
				registerHandleName(resultHandle, name);
			} else {
				resultHandle = new DefaultResultHandle<>(this, executorService, callable);
				registerHandleName(resultHandle, name);
				resultHandle.onCompletion(() -> onCompletion(resultHandle));
				resultHandle.onException(this::onException);
				handleDependencyManager.addDependencies(resultHandle, dependencies);
				boolean allDependenciesCompleted = dependencies.stream().allMatch(Handle::hasCompleted);
				if (allDependenciesCompleted) {
					scheduleForSubmission(resultHandle);
				}
			}
			return resultHandle;
		}
	}

	private String getTaskName(ExecutionConfiguration configuration) {
		Optional<String> taskName = configuration.getName();
		String nameSuggestion = taskName.orElse(createGenericTaskName());
		return createUniqueTaskName(nameSuggestion);
	}

	private String createUniqueTaskName(String suggestion) {
		String name = suggestion;
		Collection<String> existingNames = handleNamesByHashCode.values();
		int index = 2;
		while (existingNames.contains(name)) {
			name = suggestion + " (" + index++ + ")";
		}
		return name;
	}

	private ExecutorServiceWrapper getExecutorServiceWrapper(ExecutionConfiguration configuration) {
		final int taskType = configuration.getTaskType();
		return Preconditions.checkNotNull(executorServiceWrappersByTaskType.get(taskType),
			"No executor service registered for task type " + taskType + ". Use TaskType.REGULAR or TaskType.IO or a custom type you have registered an executor service for.");
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

	@Override
	public void permitTaskSubmission(boolean permit) {
		synchronized (this) {
			permitTaskSubmission = permit;
			if (permit) {
				pendingHandles.forEach(Handle::submit);
				pendingHandles.clear();
			}
		}
	}

	/**
	 * Must be called with a lock on {@code this}. Submits the handle if {@link #permitTaskSubmission}
	 * is {@code true} or collects it for later submission otherwise.
	 */
	private void scheduleForSubmission(Handle handle) {
		if (permitTaskSubmission) {
			handle.submit();
		} else {
			pendingHandles.add(handle);
		}
	}

	private String createGenericTaskName() {
		return "Task " + (handleDependencyManager.getManagedHandles().size() + 1);
	}

	<T> ResultHandle<T> createStoppedHandle() {
		return new StoppedResultHandle<>(this);
	}

	private void checkException() {
		if (exception != null) {
			Exceptions.throwUnchecked(exception);
		}
	}

	private void onException(Throwable exception) {
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
			executableHandles.forEach(this::scheduleForSubmission);
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
	public void close() {
		Throwable throwable = null;
		try {
			if (!permitTaskSubmission) {
				stop();
			}
			Collection<Handle> managedHandles = handleDependencyManager.getManagedHandles();
			for (Handle handle : managedHandles) {
				while (!handle.hasCompleted() && !handle.hasStopped()) {
					checkException();
				}
			}
			checkException();
		} finally {
			for (ExecutorServiceWrapper executorServiceWrapper : executorServiceWrappersByTaskType.values()) {
				try {
					executorServiceWrapper.close();
				} catch (Throwable t) {
					if (throwable == null) {
						throwable = t;
					}
				}
			}
		}
		if (throwable != null) {
			throw new IllegalStateException("Exception when closing executor services: " + throwable.getMessage(), throwable);
		}
	}

	/**
	 * The following methods are only syntactic sugar for simplifying the calls and
	 * delegate to a real builder.
	 */
	@Override
	public final <E extends Exception> Handle execute(ExceptionalRunnable<E> runnable) throws E {
		return configure().execute(runnable);
	}

	@Override
	public final <E extends Exception> Handle execute(StoppableExceptionalRunnable<E> runnable) throws E {
		return configure().execute(runnable);
	}

	@Override
	public final <V, E extends Exception> ResultHandle<V> execute(ExceptionalCallable<V, E> callable) throws E {
		return configure().execute(callable);
	}

	@Override
	public final <V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable) throws E {
		return configure().execute(callable);
	}

	@Override
	public ExecutionConfigurationBuilder<?> configure() {
		return new ExecutionConfigurationBuilderImpl<>(this);
	}
}
