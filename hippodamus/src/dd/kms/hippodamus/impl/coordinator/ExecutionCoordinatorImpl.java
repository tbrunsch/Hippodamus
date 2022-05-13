package dd.kms.hippodamus.impl.coordinator;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
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

	/**
	 * Handles the dependencies between handles.
	 */
	private final HandleDependencyManager	_handleDependencyManager		= new HandleDependencyManager();

	/**
	 * Contains human-friendly, by default generic task names.
	 */
	private final Set<String>				_taskNames						= new HashSet<>();

	/**
	 * Describes whether tasks that are eligible for execution may be submitted to an {@link ExecutorService}.
	 * If not, the handles of these tasks will be collected in {@link #_pendingHandles}.
	 */
	private boolean							_permitTaskSubmission			= true;

	/**
	 * This field contains all handles that can already be submitted, but whose submission is denied
	 * because {@link #_permitTaskSubmission} is {@code false}. These handles will be submitted as
	 * soon as {@code permitTaskSubmission} is set to {@code true}.
	 */
	private final List<ResultHandleImpl<?>>	_pendingHandles					= new ArrayList<>();

	/**
	 * In this field all information about exceptional situations is collected.
	 */
	private final ExceptionalState			_exceptionalState				= new ExceptionalState();

	/**
	 * Stores whether the coordinator has been requested to stop. This does not mean that it has already
	 * stopped, but it means that tasks that are not yet executing won't execute anymore.
	 */
	private boolean							_stopped						= false;

	/**
	 * This lock is held by all managed tasks. The coordinator will wait in its {@link #close()} method until
	 * all tasks have released it. Handles will release it when terminating, either successfully or exceptionally.
	 */
	private final Semaphore					terminationLock			= new Semaphore(MAX_NUM_TASKS);

	public ExecutionCoordinatorImpl(Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies) {
		this.executorServiceWrappersByTaskType = executorServiceWrappersByTaskType;
		this.logger = logger;
		this.minimumLogLevel = minimumLogLevel;
		this.verifyDependencies = verifyDependencies;
	}

	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable, TaskConfiguration taskConfiguration) {
		return execute(callable, taskConfiguration, false);
	}

	<V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable, TaskConfiguration taskConfiguration, boolean initiallyStopped) {
		ExecutorServiceWrapper executorServiceWrapper = getExecutorServiceWrapper(taskConfiguration);
		Collection<Handle> dependencies = taskConfiguration.getDependencies();
		synchronized (this) {
			checkException();
			int taskIndex = _handleDependencyManager.getNumberOfManagedHandles();
			String taskName = ExecutionCoordinatorUtils.generateTaskName(taskConfiguration, taskIndex, _taskNames);
			ResultHandleImpl<V> resultHandle = new ResultHandleImpl<>(this, taskName, taskIndex, executorServiceWrapper, callable, verifyDependencies);
			_handleDependencyManager.addDependencies(resultHandle, dependencies);
			if (!_hasStopped() && dependencies.stream().allMatch(Handle::hasCompleted)) {
				_scheduleForSubmission(resultHandle);
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
			_permitTaskSubmission = permit;
			if (permit) {
				_pendingHandles.forEach(ResultHandleImpl::submit);
				_pendingHandles.clear();
			}
		}
	}

	/**
	 * Submits the handle if {@link #_permitTaskSubmission} is {@code true} or collects it for later submission otherwise.
	 */
	private void _scheduleForSubmission(ResultHandleImpl<?> handle) {
		if (_permitTaskSubmission) {
			handle.submit();
		} else {
			_pendingHandles.add(handle);
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

	@Override
	public void checkException() {
		synchronized (this) {
			_exceptionalState.checkException();
		}
	}

	public void onCompletion(Handle handle) {
		synchronized (this) {
			List<Handle> executableHandles = _handleDependencyManager.getExecutableHandles(handle);
			for (Handle executableHandle : executableHandles) {
				_scheduleForSubmission((ResultHandleImpl<?>) executableHandle);
			}
		}
	}

	public void onException(Handle handle) {
		synchronized (this) {
			_onException(handle.getException(), false);
		}
	}

	private void _onException(Throwable exception, boolean isInternalException) {
		if (_exceptionalState.setException(exception, isInternalException)) {
			stop();
		}
	}

	@Override
	public void stop() {
		synchronized (this) {
			Collection<Handle> managedHandles = _handleDependencyManager.getManagedHandles();
			for (Handle managedHandle : managedHandles) {
				((ResultHandleImpl<?>) managedHandle).stop();
			}
			_stopped = true;
		}
	}

	public final boolean _hasStopped() {
		return _stopped;
	}

	/**
	 * Logs a message for a certain handle at a certain log message.
	 */
	public void _log(LogLevel logLevel, Handle handle, String message) {
		if (minimumLogLevel.compareTo(logLevel) < 0) {
			return;
		}
		if (_exceptionalState.isLoggerFaulty()) {
			return;
		}

		String name = handle.getTaskName();
		try {
			logger.log(logLevel, name, message);
			if (logLevel == LogLevel.INTERNAL_ERROR){
				_onException(new CoordinatorException(message), true);
			}
		} catch (Throwable t) {
			_exceptionalState.onLoggerException(t);
		}
	}

	@Override
	public void close() {
		Throwable throwable = null;
		try {
			if (!_permitTaskSubmission) {
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
