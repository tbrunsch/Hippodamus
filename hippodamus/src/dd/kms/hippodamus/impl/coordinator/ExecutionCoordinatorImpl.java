package dd.kms.hippodamus.impl.coordinator;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.exceptions.ExceptionalCallable;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.handles.TaskStage;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.impl.execution.configuration.ExecutionConfigurationBuilderImpl;
import dd.kms.hippodamus.impl.execution.configuration.TaskConfiguration;
import dd.kms.hippodamus.impl.handles.HandleImpl;
import dd.kms.hippodamus.impl.resources.ResourceShare;

public class ExecutionCoordinatorImpl implements ExecutionCoordinator
{
	private static final int	MAX_NUM_TASKS	= Integer.MAX_VALUE;

	private final Map<TaskType, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private final Logger								_logger;
	private final boolean								verifyDependencies;

	/**
	 * Handles the dependencies between handles.
	 */
	private final HandleDependencyManager				_handleDependencyManager		= new HandleDependencyManager();

	/**
	 * Contains human-friendly, by default generic task names.
	 */
	private final Set<String>							_taskNames						= new HashSet<>();

	/**
	 * Describes whether tasks that are eligible for execution may be submitted to an {@link ExecutorService}.
	 * If not, the handles of these tasks will be collected in {@link #_pendingHandles}.
	 */
	private boolean										_permitTaskSubmission			= true;

	/**
	 * This field contains all handles that can already be submitted, but whose submission is denied
	 * because {@link #_permitTaskSubmission} is {@code false}. These handles will be submitted as
	 * soon as {@code permitTaskSubmission} is set to {@code true}.
	 */
	private final List<HandleImpl<?>>					_pendingHandles					= new ArrayList<>();

	/**
	 * In this field all information about exceptional situations is collected.
	 */
	private final ExceptionalState						_exceptionalState				= new ExceptionalState();

	/**
	 * Stores whether the coordinator has been requested to stop. This does not mean that it has already
	 * stopped, but it means that tasks that are not yet executing won't execute anymore.
	 */
	private boolean										_stopped						= false;

	/**
	 * This lock is held by all managed tasks. The coordinator will wait in its {@link #close()} method until
	 * all tasks have released it. Handles will release it when terminating, either successfully or exceptionally.
	 */
	private final Semaphore								terminationLock					= new Semaphore(MAX_NUM_TASKS);

	public ExecutionCoordinatorImpl(Map<TaskType, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, boolean verifyDependencies) {
		this.executorServiceWrappersByTaskType = executorServiceWrappersByTaskType;
		this._logger = logger;
		this.verifyDependencies = verifyDependencies;
	}

	public <V, T extends Throwable> ResultHandle<V> execute(ExceptionalCallable<V, T> callable, TaskConfiguration taskConfiguration) {
		ExecutorServiceWrapper executorServiceWrapper = getExecutorServiceWrapper(taskConfiguration);
		Collection<Handle> dependencies = taskConfiguration.getDependencies();
		ResourceShare resourceShare = taskConfiguration.getRequiredResourceShare();
		Consumer<Handle> handleConsumer = taskConfiguration.getHandleConsumer();
		synchronized (this) {
			checkException();
			int taskIndex = _handleDependencyManager.getNumberOfManagedHandles();
			String taskName = ExecutionCoordinatorUtils.generateTaskName(taskConfiguration, taskIndex, _taskNames);
			boolean ignoreResult = taskConfiguration.isIgnoreResult();
			HandleImpl<V> resultHandle = new HandleImpl<>(this, taskName, taskIndex, executorServiceWrapper, callable, resourceShare, verifyDependencies, ignoreResult);
			// propagate handle immediately after its creation before any logging or task execution
			handleConsumer.accept(resultHandle);
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
				_pendingHandles.forEach(HandleImpl::submit);
				_pendingHandles.clear();
			}
		}
	}

	/**
	 * Submits the handle if {@link #_permitTaskSubmission} is {@code true} or collects it for later submission otherwise.
	 */
	private void _scheduleForSubmission(HandleImpl<?> handle) {
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
				_scheduleForSubmission((HandleImpl<?>) executableHandle);
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
				((HandleImpl<?>) managedHandle)._stop();
			}
			_stopped = true;
		}
	}

	public final boolean _hasStopped() {
		return _stopped;
	}

	public void _log(Handle handle, String message) {
		if (_exceptionalState.isLoggerFaulty()) {
			return;
		}

		try {
			_logger.log(handle, message);
		} catch (Throwable t) {
			_exceptionalState.onLoggerException(t);
		}
	}

	public void _logStateChange(Handle handle, TaskStage stage) {
		if (_exceptionalState.isLoggerFaulty()) {
			return;
		}

		try {
			_logger.logStateChange(handle, stage);
		} catch (Throwable t) {
			_exceptionalState.onLoggerException(t);
		}
	}

	public void _logError(Handle handle, String error, @Nullable Throwable cause) {
		if (_exceptionalState.isLoggerFaulty()) {
			return;
		}

		try {
			_logger.logError(handle, error, cause);
			_onException(new CoordinatorException(error, cause), true);
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
				Thread.currentThread().interrupt();
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
