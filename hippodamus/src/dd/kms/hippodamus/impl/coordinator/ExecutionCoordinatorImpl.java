package dd.kms.hippodamus.impl.coordinator;

import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.exceptions.*;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.exceptions.Exceptions;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.impl.execution.configuration.ExecutionConfiguration;
import dd.kms.hippodamus.impl.execution.configuration.ExecutionConfigurationBuilderImpl;
import dd.kms.hippodamus.impl.handles.Handles;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

public class ExecutionCoordinatorImpl implements InternalCoordinator
{
	private static final int	MAX_NUM_TASKS	= Integer.MAX_VALUE;

	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private final Logger								logger;
	private final LogLevel								minimumLogLevel;
	private final boolean								verifyDependencies;
	private final WaitMode								waitMode;

	/**
	 * Handles the dependencies between handles.<br>
	 * <br>
	 * The state of the dependency manager will only be changed by calls to {@link #execute(StoppableExceptionalCallable, ExecutionConfiguration)},
	 * which is only called in the coordinator's thread. Hence, the cached state of the dependency manager
	 * will always be coherent in the coordinator's thread. No {@code synchronized}-block is required to
	 * access the dependency manager in methods that are only called in the coordinator's thread.
	 */
	private final HandleDependencyManager	handleDependencyManager			= new HandleDependencyManager();

	/**
	 * Contains human-friendly, by default generic task names.<br>
	 * <br>
	 * The state of that set will only be changed by calls to {@link #execute(StoppableExceptionalCallable, ExecutionConfiguration)},
	 * which is only called in the coordinator's thread. Hence, the cached state of the set
	 * will always be coherent in the coordinator's thread. No {@code synchronized}-block is
	 * required to access the map in methods that are only called in the coordinator's thread.
	 */
	private final Set<String>				handleNames						= new HashSet<>();

	/**
	 * Describes whether tasks that are eligible for execution may be submitted to an {@link ExecutorService}.
	 * If not, the handles of these tasks will be collected in {@link #pendingHandles}.<br>
	 * <br>
	 * The flag will only be changed by calls to {@link #permitTaskSubmission(boolean)}, which is only called
	 * in the coordinator's thread. Hence, the cached value will always be coherent in the coordinator's thread.
	 * No {@code synchronized}-block is required to access the value in methods that are only called in the
	 * coordinator's thread.
	 */
	private boolean							permitTaskSubmission			= true;

	/**
	 * This field contains all handles that can already be submitted, but whose submission is denied
	 * because {@link #permitTaskSubmission} is {@code false}. These handles will be submitted as
	 * soon as {@code permitTaskSubmission} is set to {@code true}.
	 */
	private final List<Handle>				pendingHandles					= new ArrayList<>();

	/**
	 * The field may be set from a different thread than the one the coordinator is running in.
	 * However, the coordinator will regularly check it
	 * <ul>
	 *     <li>whenever a new task is added or</li>
	 *     <li>when the coordinator is closing</li>
	 * </ul>
	 */
	private volatile Throwable				exception;

	/**
	 * This field is required to ensure that {@link #checkException()} does not throw an exception twice.<br>
	 * <br>
	 * <b>Necessity:</b> Assume that an exception is thrown inside the try-block due to a call of
	 * {@link #checkException()}. This will cause the {@link #close()} method to be called automatically,
	 * which also checks for an existing exception to be thrown by calling {@code checkException()}. If this
	 * method would throw the stored exception again, then we had two exceptions to be thrown. Such conflicts
	 * are resolved by automatically calling {@link Throwable#addSuppressed(Throwable)}. However, this method
	 * fails if both exceptions are identical. In that case, we would obtain an {@link IllegalArgumentException}
	 * "Self-suppression not permitted" instead, which is not what we want.<br>
	 * <br>
	 * Since this flag is only used within method {@code checkException()}, which is only meant to be called
	 * in the coordinator's thread, cache coherence is guaranteed.
	 */
	private boolean							hasThrownException;

	/**
	 * This flag is set to true if at any point the logger threw an exception when logging. In that case,
	 * we do not try to log further messages to avoid further exceptions.<br>
	 * <br>
	 * Since this flag is only used within the method {@link #log(LogLevel, Handle, String)} and this
	 * method must be called with logging the coordinator, cache coherence is guaranteed.
	 */
	private boolean							loggerFaulty;

	/**
	 * This lock is hold by all managed tasks. The coordinator will wait in its {@link #close()} method until
	 * all tasks have released it. Handles will release it when terminating, either successfully or exceptionally,
	 * or when being stopped if the coordinator is configured with {@link WaitMode#UNTIL_TERMINATION_REQUESTED}.
	 */
	private final Semaphore					terminationLock			= new Semaphore(MAX_NUM_TASKS);

	public ExecutionCoordinatorImpl(Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies, WaitMode waitMode) {
		this.executorServiceWrappersByTaskType = executorServiceWrappersByTaskType;
		this.logger = logger;
		this.minimumLogLevel = minimumLogLevel;
		this.verifyDependencies = verifyDependencies;
		this.waitMode = waitMode;
	}

	public <V, T extends Throwable> ResultHandle<V> execute(StoppableExceptionalCallable<V, T> callable, ExecutionConfiguration configuration) {
		return execute(callable, configuration, false);
	}

	<V, T extends Throwable> ResultHandle<V> execute(StoppableExceptionalCallable<V, T> callable, ExecutionConfiguration configuration, boolean initiallyStopped) {
		ExecutorServiceWrapper executorServiceWrapper = getExecutorServiceWrapper(configuration);
		String taskName = getTaskName(configuration);
		Collection<Handle> dependencies = configuration.getDependencies();
		synchronized (this) {
			checkException();
			boolean stopped = initiallyStopped || dependencies.stream().anyMatch(Handle::hasStopped);
			int id = handleDependencyManager.getNumberOfManagedHandles();
			ResultHandle<V> resultHandle = Handles.createResultHandle(this, taskName, id, executorServiceWrapper, callable, verifyDependencies, stopped);
			handleDependencyManager.addDependencies(resultHandle, dependencies);
			if (!stopped) {
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
	String getTaskName(ExecutionConfiguration configuration) {
		Optional<String> taskName = configuration.getName();
		String nameSuggestion = taskName.orElse(createGenericTaskName());
		return createUniqueTaskName(nameSuggestion);
	}

	private String createUniqueTaskName(String suggestion) {
		String name = suggestion;
		int index = 2;
		while (handleNames.contains(name)) {
			name = suggestion + " (" + index++ + ")";
		}
		handleNames.add(name);
		return name;
	}

	private String createGenericTaskName() {
		return "Task " + (handleDependencyManager.getManagedHandles().size() + 1);
	}

	@Override
	public boolean supportsTaskType(int taskType) {
		return executorServiceWrappersByTaskType.get(taskType) != null;
	}

	private ExecutorServiceWrapper getExecutorServiceWrapper(ExecutionConfiguration configuration) {
		int taskType = configuration.getTaskType();
		ExecutorServiceWrapper executorServiceWrapper = executorServiceWrappersByTaskType.get(taskType);
		if (executorServiceWrapper == null) {
			throw new CoordinatorException("Internal error: No executor service registered for task type " + taskType + ". This should not happen because ExecutionConfigurationBuilder.taskType(int) checks this.");
		}
		return executorServiceWrapper;
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

	@Override
	public Semaphore getTerminationLock() {
		return terminationLock;
	}

	@Override
	public WaitMode getWaitMode() {
		return waitMode;
	}

	@Override
	public void checkException() {
		if (exception != null && !hasThrownException) {
			hasThrownException = true;
			Exceptions.throwUnchecked(exception);
		}
	}

	@Override
	public void onCompletion(Handle handle) {
		synchronized (this) {
			List<Handle> executableHandles = handleDependencyManager.getExecutableHandles(handle);
			executableHandles.forEach(this::scheduleForSubmission);
		}
	}

	@Override
	public void onException(Handle handle) {
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

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	@Override
	public void log(LogLevel logLevel, Handle handle, String message) {
		if (minimumLogLevel.compareTo(logLevel) < 0) {
			return;
		}
		String name = handle.getTaskName();
		CoordinatorException exception = null;
		try {
			if (!loggerFaulty) {
				logger.log(logLevel, name, message);
				if (logLevel == LogLevel.INTERNAL_ERROR){
					exception = new CoordinatorException(message);
				}
			}
		} catch (Throwable t) {
			loggerFaulty = true;
			exception = new CoordinatorException("Exception in logger: " + t.getMessage(), t);
		}
		if (exception != null) {
			/*
			 * The internal exception should overwrite a potential task exception because it indicates a fundamental
			 * problem that must be fixed and must therefore not be hidden.
			 */
			onException(exception, true);
		}
	}

	@Override
	public void close() {
		Throwable throwable = null;
		try {
			if (!permitTaskSubmission) {
				stop();
			}
			try {
				terminationLock.acquire(MAX_NUM_TASKS);
			} catch (InterruptedException e) {
				stop();
				terminationLock.acquireUninterruptibly(MAX_NUM_TASKS);
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
			throw new CoordinatorException("Exception when closing executor services: " + throwable.getMessage(), throwable);
		}
	}

	/**
	 * The following methods are only syntactic sugar for simplifying the calls and
	 * delegate to a real builder.
	 */
	@Override
	public final <T extends Throwable> Handle execute(ExceptionalRunnable<T> runnable) throws T {
		return configure().execute(runnable);
	}

	@Override
	public final <T extends Throwable> Handle execute(StoppableExceptionalRunnable<T> runnable) throws T {
		return configure().execute(runnable);
	}

	@Override
	public final <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) throws T {
		return configure().execute(callable);
	}

	@Override
	public final <V, T extends Throwable> ResultHandle<V> execute(StoppableExceptionalCallable<V, T> callable) throws T {
		return configure().execute(callable);
	}

	@Override
	public ExecutionConfigurationBuilder configure() {
		return new ExecutionConfigurationBuilderImpl(this);
	}
}
