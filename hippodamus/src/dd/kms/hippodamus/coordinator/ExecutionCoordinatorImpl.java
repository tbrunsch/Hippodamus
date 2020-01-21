package dd.kms.hippodamus.coordinator;

import com.google.common.base.Preconditions;
import dd.kms.hippodamus.coordinator.configuration.CoordinatorConfiguration;
import dd.kms.hippodamus.exceptions.*;
import dd.kms.hippodamus.execution.configuration.ExecutionConfiguration;
import dd.kms.hippodamus.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.execution.configuration.ExecutionConfigurationBuilderImpl;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.handles.impl.DefaultResultHandle;
import dd.kms.hippodamus.handles.impl.StoppedResultHandle;
import dd.kms.hippodamus.logging.LogLevel;
import dd.kms.hippodamus.logging.Logger;

import java.util.*;

public class ExecutionCoordinatorImpl implements ExecutionCoordinator
{
	private final CoordinatorConfiguration coordinatorConfiguration;

	private final HandleDependencyManager			handleDependencyManager;
	private final Map<Integer, String>				handleNamesByHashCode			= new HashMap<>();

	private boolean									permitTaskSubmission			= true;

	/**
	 * This field contains all handles that can already be submitted, but whose submission is denied
	 * because {@link #permitTaskSubmission} is {@code false}. These handles will be submitted as
	 * soon as {@code permitTaskSubmission} is set to {@code true}.
	 */
	private final List<Handle>						pendingHandles					= new ArrayList<>();

	/**
	 * The field may be set from a different thread than the one the coordinator is running in.
	 * However, the coordinator will regularly check it
	 * <ul>
	 *     <li>whenever a new task is added or</li>
	 *     <li>when the coordinator is closing</li>
	 * </ul>
	 */
	private Throwable								exception;

	/**
	 * This field is required to ensure that {@link #checkException()} does not throw an exception twice.<br/>
	 * <br/>
	 * <b>Necessity:</b> Assume that an exception is thrown inside the try-block due to a call of
	 * {@link #checkException()}. This will cause the {@link #close()} method to be called automatically,
	 * which also checks for an existing exception to be thrown by calling {@code checkException()}. If this
	 * method would throw the stored exception again, then we had two exceptions to be thrown. Such conflicts
	 * are resolved by automatically calling {@link Throwable#addSuppressed(Throwable)}. However, this method
	 * fails if both exceptions are identical. In that case, we would obtain an {@link IllegalArgumentException}
	 * "Self-suppression not permitted" instead, which is not what we want.
	 */
	private boolean									hasThrownException;

	private boolean									closing;

	public ExecutionCoordinatorImpl(CoordinatorConfiguration coordinatorConfiguration) {
		this.coordinatorConfiguration = coordinatorConfiguration;
		this.handleDependencyManager = new HandleDependencyManager(this);
	}

	// TODO: Coordinator should reject this if it has already been closed
	public <V, E extends Exception> ResultHandle<V> execute(StoppableExceptionalCallable<V, E> callable, ExecutionConfiguration configuration) {
		ExecutorServiceWrapper executorServiceWrapper = getExecutorServiceWrapper(configuration);
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
				boolean verifyDependencies = coordinatorConfiguration.isVerifyDependencies();
				resultHandle = new DefaultResultHandle<>(this, executorServiceWrapper, callable, verifyDependencies);
				registerHandleName(resultHandle, name);
				resultHandle.onCompletion(() -> onCompletion(resultHandle));
				resultHandle.onException(() -> onException(resultHandle));
				handleDependencyManager.addDependencies(resultHandle, dependencies);
				boolean allDependenciesCompleted = dependencies.stream().allMatch(Handle::hasCompleted);
				if (allDependenciesCompleted) {
					scheduleForSubmission(resultHandle);
				}
			}
			return resultHandle;
		}
	}

	/*
	 * Task names
	 */
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

	private String createGenericTaskName() {
		return "Task " + (handleDependencyManager.getManagedHandles().size() + 1);
	}

	private void registerHandleName(Handle handle, String name) {
		int hashCode = System.identityHashCode(handle);
		handleNamesByHashCode.put(hashCode, name);
	}

	@Override
	public String getTaskName(Handle handle) {
		if (closing) {
			// no handles will be added anymore => no synchronization required to obtain handle name
			return doGetTaskName(handle);
		} else {
			synchronized (this) {
				return doGetTaskName(handle);
			}
		}
	}

	private String doGetTaskName(Handle handle) {
		int hashCode = System.identityHashCode(handle);
		String name = handleNamesByHashCode.get(hashCode);
		return name == null ? "Unknown handle " + hashCode : name;
	}

	private ExecutorServiceWrapper getExecutorServiceWrapper(ExecutionConfiguration configuration) {
		int taskType = configuration.getTaskType();
		Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType = coordinatorConfiguration.getExecutorServiceWrappersByTaskType();
		return Preconditions.checkNotNull(executorServiceWrappersByTaskType.get(taskType),
			"No executor service registered for task type " + taskType + ". Use TaskType.REGULAR or TaskType.IO or a custom type you have registered an executor service for.");
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

	<T> ResultHandle<T> createStoppedHandle() {
		boolean verifyDependencies = coordinatorConfiguration.isVerifyDependencies();
		return new StoppedResultHandle<>(this, verifyDependencies);
	}

	@Override
	public void checkException() {
		if (exception != null && !hasThrownException) {
			hasThrownException = true;
			Exceptions.throwUnchecked(exception);
		}
	}

	private void onCompletion(Handle handle) {
		synchronized (this) {
			List<Handle> executableHandles = handleDependencyManager.getExecutableHandles(handle);
			executableHandles.forEach(this::scheduleForSubmission);
		}
	}

	private void onException(Handle handle) {
		onException(handle.getException(), false);
	}

	private void onException(Throwable exception, boolean overwriteIfExists) {
		synchronized (this) {
			if (overwriteIfExists || this.exception == null) {
				this.exception = exception;
				stop();
			}
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
		LogLevel minimumLogLevel = coordinatorConfiguration.getMinimumLogLevel();
		if (minimumLogLevel.compareTo(logLevel) < 0) {
			return;
		}
		String name = getTaskName(handle);
		Logger logger = coordinatorConfiguration.getLogger();
		logger.log(logLevel, name, message);
		if (logLevel == LogLevel.INTERNAL_ERROR) {
			/*
			 * The internal exception should overwrite a potential task exception because it indicates a fundamental
			 * problem that must be fixed and must therefore not be hidden.
			 */
			onException(new IllegalStateException(message), true);
			stop();
		}
	}

	@Override
	public void close() {
		closing = true;
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
			Collection<ExecutorServiceWrapper> executorServiceWrappers = coordinatorConfiguration.getExecutorServiceWrappersByTaskType().values();
			for (ExecutorServiceWrapper executorServiceWrapper : executorServiceWrappers) {
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
