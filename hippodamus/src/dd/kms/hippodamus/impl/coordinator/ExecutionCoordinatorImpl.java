package dd.kms.hippodamus.impl.coordinator;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.impl.execution.configuration.ExecutionConfigurationBuilderImpl;
import dd.kms.hippodamus.impl.execution.configuration.TaskConfiguration;
import dd.kms.hippodamus.impl.handles.ResultHandleImpl;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

public class ExecutionCoordinatorImpl implements ExecutionCoordinator
{
	private static final int	MAX_NUM_TASKS	= Integer.MAX_VALUE;

	private final Map<TaskType, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private final Logger								logger;
	private final LogLevel								minimumLogLevel;
	private final boolean								verifyDependencies;
	private final WaitMode								waitMode;

	/**
	 * Handles the dependencies between handles.<br>
	 * <br>
	 * The state of the dependency manager will only be changed by calls to {@link #execute(ExceptionalCallable, TaskConfiguration)},
	 * which is only called in the coordinator's thread. Hence, the state of the dependency manager
	 * will always be coherent in the coordinator's thread. No {@code synchronized}-block is required to
	 * access the dependency manager in methods that are only called in the coordinator's thread.
	 */
	private final HandleDependencyManager	handleDependencyManager			= new HandleDependencyManager();

	/**
	 * Contains human-friendly, by default generic task names.<br>
	 * <br>
	 * The state of that set will only be changed by calls to {@link #execute(ExceptionalCallable, TaskConfiguration)},
	 * which is only called in the coordinator's thread. Hence, the state of the set
	 * will always be coherent in the coordinator's thread. No {@code synchronized}-block is
	 * required to access the map in methods that are only called in the coordinator's thread.
	 */
	private final Set<String>				taskNames						= new HashSet<>();

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
	private final List<ResultHandleImpl<?>>	pendingHandles					= new ArrayList<>();

	/**
	 * In this field all information about exceptional situations is collected.
	 */
	private final ExceptionalState			exceptionalState				= new ExceptionalState();

	/**
	 * This lock is held by all managed tasks. The coordinator will wait in its {@link #close()} method until
	 * all tasks have released it. Handles will release it when terminating, either successfully or exceptionally,
	 * or when being stopped if the coordinator is configured with {@link WaitMode#UNTIL_TERMINATION_REQUESTED}.
	 */
	private final Semaphore					terminationLock			= new Semaphore(MAX_NUM_TASKS);

	public ExecutionCoordinatorImpl(Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies, WaitMode waitMode) {
		this.executorServiceWrappersByTaskType = executorServiceWrappersByTaskType;
		this.logger = logger;
		this.minimumLogLevel = minimumLogLevel;
		this.verifyDependencies = verifyDependencies;
		this.waitMode = waitMode;
	}

	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable, TaskConfiguration taskConfiguration) {
		return execute(callable, taskConfiguration, false);
	}

	<V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable, TaskConfiguration taskConfiguration, boolean initiallyStopped) {
		ExecutorServiceWrapper executorServiceWrapper = getExecutorServiceWrapper(taskConfiguration);
		Collection<Handle> dependencies = taskConfiguration.getDependencies();
		synchronized (this) {
			checkException();
			boolean stopped = initiallyStopped || dependencies.stream().anyMatch(Handle::hasStopped);
			int taskIndex = handleDependencyManager.getNumberOfManagedHandles();
			String taskName = ExecutionCoordinatorUtils.generateTaskName(taskConfiguration, taskIndex, taskNames);
			ResultHandleImpl<V> resultHandle = new ResultHandleImpl<>(this, taskName, taskIndex, executorServiceWrapper, callable, verifyDependencies, stopped);
			handleDependencyManager.addDependencies(resultHandle, dependencies);
			if (!stopped && dependencies.stream().allMatch(Handle::hasCompleted)) {
				scheduleForSubmission(resultHandle);
			}
			return resultHandle;
		}
	}

	public boolean supportsTaskType(TaskType taskType) {
		return executorServiceWrappersByTaskType.containsKey(taskType);
	}

	private ExecutorServiceWrapper getExecutorServiceWrapper(TaskConfiguration taskConfiguration) {
		TaskType taskType = taskConfiguration.getTaskType();
		ExecutorServiceWrapper executorServiceWrapper = executorServiceWrappersByTaskType.get(taskType);
		if (executorServiceWrapper != null) {
			return executorServiceWrapper;
		}
		throw new CoordinatorException("Internal error: No executor service registered for task type " + taskType + ". This should not happen because ExecutionConfigurationBuilder.taskType(TaskType) checks this.");
	}

	@Override
	public void permitTaskSubmission(boolean permit) {
		synchronized (this) {
			permitTaskSubmission = permit;
			if (permit) {
				pendingHandles.forEach(ResultHandleImpl::submit);
				pendingHandles.clear();
			}
		}
	}

	/**
	 * Must be called with a lock on {@code this}. Submits the handle if {@link #permitTaskSubmission}
	 * is {@code true} or collects it for later submission otherwise.
	 */
	private void scheduleForSubmission(ResultHandleImpl<?> handle) {
		if (permitTaskSubmission) {
			handle.submit();
		} else {
			pendingHandles.add(handle);
		}
	}

	/**
	 * @return The coordinators termination lock. This lock is held by all handles managed by the coordinator.
	 * The coordinator will wait in its {@link #close()} method until all tasks have released it.<br>
	 * <br>
	 * Handles must release it when terminating, either successfully or exceptionally, or when being stopped.
	 */
	public Semaphore getTerminationLock() {
		return terminationLock;
	}

	public WaitMode getWaitMode() {
		return waitMode;
	}

	@Override
	public synchronized void checkException() {
		exceptionalState.checkException();
	}

	public void onCompletion(Handle handle) {
		synchronized (this) {
			List<Handle> executableHandles = handleDependencyManager.getExecutableHandles(handle);
			for (Handle executableHandle : executableHandles) {
				scheduleForSubmission((ResultHandleImpl<?>) executableHandle);
			}
		}
	}

	public void onException(Handle handle) {
		synchronized (this) {
			onException(handle.getException(), false);
		}
	}

	/*
	 * Ensure that this method is called when the coordinator is locked.
	 */
	private void onException(Throwable exception, boolean isInternalException) {
		if (exceptionalState.setException(exception, isInternalException)) {
			stop();
		}
	}

	@Override
	public void stop() {
		synchronized (this) {
			Collection<Handle> managedHandles = handleDependencyManager.getManagedHandles();
			managedHandles.forEach(Handle::stop);
		}
	}

	/**
	 * Stops all dependent handles of the specified handle if the handle has been created by this service.
	 */
	public void stopDependentHandles(Handle handle) {
		synchronized (this) {
			List<Handle> dependentHandles = handleDependencyManager.getDependentHandles(handle);
			dependentHandles.forEach(Handle::stop);
		}
	}

	/**
	 * Logs a message for a certain handle at a certain log message.<br>
	 * <br>
	 * Ensure that this method is only called with locking the coordinator.
	 */
	public void log(LogLevel logLevel, Handle handle, String message) {
		if (minimumLogLevel.compareTo(logLevel) < 0) {
			return;
		}
		if (exceptionalState.isLoggerFaulty()) {
			return;
		}

		String name = handle.getTaskName();
		try {
			logger.log(logLevel, name, message);
			if (logLevel == LogLevel.INTERNAL_ERROR){
				onException(new CoordinatorException(message), true);
			}
		} catch (Throwable t) {
			exceptionalState.onLoggerException(t);
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

	@Override
	public final <T extends Throwable> Handle execute(ExceptionalRunnable<T> runnable) throws T {
		return configure().execute(runnable);
	}

	@Override
	public final <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable) throws T {
		return configure().execute(callable);
	}

	@Override
	public ExecutionConfigurationBuilder configure() {
		return new ExecutionConfigurationBuilderImpl(this);
	}
}
