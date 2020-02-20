package dd.kms.hippodamus.handles;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.exceptions.StoppableExceptionalCallable;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.execution.InternalTaskHandle;
import dd.kms.hippodamus.logging.LogLevel;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

class ResultHandleImpl<T> implements ResultHandle<T>
{
	private static final Consumer<Handle>	NO_HANDLE_CONSUMER	= handle -> {};

	private final InternalCoordinator					coordinator;
	private final String								taskName;
	private final int									id;
	private final ExecutorServiceWrapper				executorServiceWrapper;
	private final StoppableExceptionalCallable<T, ?>	callable;
	private final boolean								verifyDependencies;

	private final List<Runnable>						completionListeners					= new ArrayList<>();
	private final List<Runnable>						exceptionListeners					= new ArrayList<>();

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * is stopped. It is meant to be waited for in the {@link #join()}-method.<br/>
	 * <br/>
	 * Note that the value must be set to true <b>before</b> calling any listener to avoid deadlocks:
	 * Listeners, in particular completion listeners, might indirectly call {@code join()}, e.g., by calling
	 * {@link ResultHandleImpl#get()}.
	 */
	private final AwaitableBoolean						terminatedFlagForJoin;

	/**
	 * This value is set to true when the task terminates, either successfully or exceptionally, or
	 * is stopped. It operates on the coordinator's termination lock we obtain by calling
	 * {@link InternalCoordinator#getTerminationLock()}.<br/>
	 * Note that the value must be set to true <b>after</b> calling any listener to ensure that the
	 * coordinator does not close before notifying all listeners.
	 */
	private final AwaitableBoolean						terminatedFlagForCoordinator;

	private final ResultDescription<T>					resultDescription					= new ResultDescription<>();
	private volatile HandleStage						handleStage							= HandleStage.INITIAL;
	private volatile boolean							stopped								= false;

	private volatile InternalTaskHandle					taskHandle;

	ResultHandleImpl(InternalCoordinator coordinator, String taskName, int id, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies, boolean stopped) {
		this.coordinator = coordinator;
		this.taskName = taskName;
		this.id = id;
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
		this.verifyDependencies = verifyDependencies;
		this.stopped = stopped;

		terminatedFlagForJoin = new AwaitableBoolean(new Semaphore(1));
		terminatedFlagForCoordinator = new AwaitableBoolean(coordinator.getTerminationLock());

		if (!stopped) {
			try {
				terminatedFlagForJoin.setFalse();
				terminatedFlagForCoordinator.setFalse();
			} catch (InterruptedException e) {
				stop();
			}
		}
	}

	/*****************
	 * Stage Changes *
	 ****************/
	@Override
	public void submit() {
		synchronized (coordinator) {
			boolean success = checkState()
							&& !stopped
							&& transitionToStage(HandleStage.SUBMITTED)
							&& checkState();
			if (success) {
				taskHandle = executorServiceWrapper.submit(id, this::executeCallable);
			}
		}
	}

	/**
	 * @return true if task should be executed
	 */
	private boolean startExecution() {
		synchronized (coordinator) {
			return checkState()
				&& !stopped
				&& transitionToStage(HandleStage.EXECUTING)
				&& checkState();
		}
	}

	private void complete(T result) {
		synchronized (coordinator) {
			try {
				boolean success = checkState()
								&& checkCondition(resultDescription.setResult(result), "Cannot set result due to inconsistent state")
								&& log(LogLevel.STATE, "result = " + result)
								&& transitionToStage(HandleStage.TERMINATING)
								&& checkState();
				if (success) {
					notifyListeners(completionListeners, "completion listener", coordinator::onCompletion);
					executorServiceWrapper.onTaskCompleted();
				}
			} finally {
				transitionToStage(HandleStage.TERMINATED);
			}
		}
	}

	private void terminateExceptionally(Throwable exception) {
		synchronized (coordinator) {
			try {
				boolean success = checkState()
								&& checkCondition(resultDescription.setException(exception), "Cannot set exception due to inconsistent state")
								&& log(LogLevel.STATE, "encountered " + exception.getClass().getSimpleName() + ": " + exception.getMessage())
								&& transitionToStage(HandleStage.TERMINATING);
				if (success) {
					notifyListeners(exceptionListeners, "exception listener", coordinator::onException);
				}
			} finally {
				transitionToStage(HandleStage.TERMINATED);
			}
		}
	}

	@Override
	public final void stop() {
		synchronized (coordinator) {
			HandleStage oldHandleStage = this.handleStage;
			try {
				if (!checkState() || stopped) {
					return;
				}
				stopped = true;
				log(LogLevel.STATE, "stopped");
				if (oldHandleStage == HandleStage.EXECUTING) {
					/*
					 * Since the task is still executing and we cannot simply stop it,
					 * we cannot change to HandleStage.TERMINATED. Nevertheless, we
					 * set the terminated flag that blocks calls to join() to true
					 * because further waiting is useless.
					 */
					terminatedFlagForJoin.setTrue();
				} else {
					transitionToStage(HandleStage.TERMINATED);
				}
				coordinator.stopDependentHandles(this);
				if (taskHandle != null) {
					taskHandle.stop();
				}
			} finally {
				if (oldHandleStage == HandleStage.EXECUTING) {
					if (coordinator.getWaitMode() != WaitMode.UNTIL_TERMINATION) {
						/*
						 * Since the task is still executing and we cannot simply stop it,
						 * we cannot change to HandleStage.TERMINATED. Nevertheless, we
						 * set the terminated flag that blocks the coordinator if it does
						 * not want to wait until the task terminates.
						 */
						terminatedFlagForCoordinator.setTrue();
					}
				}
			}
		}
	}

	@Override
	public void join() {
		if (resultDescription.getResultType() == ResultType.COMPLETED) {
			return;
		}
		if (verifyDependencies) {
			synchronized (coordinator) {
				log(LogLevel.INTERNAL_ERROR, "Waiting for a handle that has not yet completed. Did you forget to specify that handle as dependency?");
				throw new TaskStoppedException(taskName);
			}
		}
		if (stopped || resultDescription.getResultType() == ResultType.EXCEPTION) {
			throw new TaskStoppedException(taskName);
		}
		terminatedFlagForJoin.waitUntilTrue();
		if (resultDescription.getResultType() != ResultType.COMPLETED) {
			throw new TaskStoppedException(taskName);
		}
	}

	@Override
	public boolean hasCompleted() {
		return resultDescription.getResultType() == ResultType.COMPLETED;
	}

	@Override
	public final boolean hasStopped() {
		return stopped;
	}

	@Override
	public @Nullable Throwable getException() {
		return resultDescription.getException();
	}

	@Override
	public String getTaskName() {
		return taskName;
	}

	@Override
	public final InternalCoordinator getExecutionCoordinator() {
		return coordinator;
	}

	@Override
	public T get() {
		join();
		checkCondition(resultDescription.getResultType() == ResultType.COMPLETED, "join() returned regularly although there is no result available");
		return resultDescription.getResult();
	}

	private void executeCallable() {
		if (!startExecution()) {
			return;
		}
		try {
			T result = callable.call(this::hasStopped);
			complete(result);
		} catch (TaskStoppedException e) {
			transitionToStage(HandleStage.TERMINATED);
			stop();
		} catch (Throwable throwable) {
			terminateExceptionally(throwable);
		}
	}

	/***********************
	 * Listener Management *
	 **********************/
	@Override
	public void onCompletion(Runnable listener) {
		synchronized (coordinator) {
			completionListeners.add(listener);
			if (resultDescription.getResultType() == ResultType.COMPLETED) {
				// only run this listener; other listeners have already been notified
				notifyListeners(Collections.singletonList(listener), "completion listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	@Override
	public void onException(Runnable listener) {
		synchronized (coordinator) {
			exceptionListeners.add(listener);
			if (resultDescription.getResultType() == ResultType.EXCEPTION) {
				// only inform this handler; other handlers have already been notified
				notifyListeners(Collections.singletonList(listener), "exception listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	/**
	 * Ensure that this method is only called when the coordinator is locked.
	 */
	private void notifyListeners(List<Runnable> listeners, String listenerDescription, Consumer<Handle> coordinatorListener) {
		Throwable listenerException = null;
		Runnable exceptionalListener = null;
		for (Runnable listener : listeners) {
			try {
				listener.run();
			} catch (Throwable t) {
				if (listenerException == null) {
					listenerException = t;
					exceptionalListener = listener;
				}
			}
		}
		if (listenerException == null) {
			coordinatorListener.accept(this);
		} else {
			String message = MessageFormat.format("{0} in {1} \"{2}\": {3}",
				listenerException.getClass().getSimpleName(),
				listenerDescription,
				exceptionalListener,
				listenerException.getMessage());
			log(LogLevel.INTERNAL_ERROR, message);
		}
	}

	/**
	 * Ensure that this method is only called when the coordinator is locked.<br/>
	 * <br/>
	 * The return value is always {@code true} such that this method can be used
	 * within Boolean expressions.
	 */
	private boolean log(LogLevel logLevel, String message) {
		coordinator.log(logLevel, this, message);
		return true;
	}

	private boolean transitionToStage(HandleStage newStage) {
		if (handleStage.compareTo(HandleStage.TERMINATING) < 0 && HandleStage.TERMINATING.compareTo(newStage) <= 0) {
			terminatedFlagForJoin.setTrue();
		}
		if (newStage == HandleStage.TERMINATED) {
			if (handleStage != HandleStage.TERMINATED) {
				terminatedFlagForCoordinator.setTrue();
			}
		} else {
			if (!checkCondition(newStage.ordinal() == handleStage.ordinal() + 1, "Trying to transition state from '" + handleStage + "' to '" + newStage + "'")) {
				return false;
			}
		}
		handleStage = newStage;
		coordinator.log(LogLevel.STATE, this, handleStage.toString());
		return true;
	}

	/***************************************************************
	 * Consistency Checks                                          *
	 *                                                             *
	 * Ensure that they are called before any concurrency problems *
	 * may occur or with locking the coordinator.                  *
	 **************************************************************/
	private boolean checkState() {
		boolean success = true;
		if (handleStage == HandleStage.INITIAL || handleStage == HandleStage.SUBMITTED || handleStage == HandleStage.EXECUTING) {
			success = success && checkCondition(resultDescription.getResultType() == ResultType.NONE,	"The result should still be undefined");
		}
		ResultType resultType = resultDescription.getResultType();
		if (resultType != ResultType.NONE) {
			success = success && checkCondition(handleStage == HandleStage.TERMINATING || handleStage == HandleStage.TERMINATED,	"The task should have terminated");
		}
		return success;
	}

	private boolean checkCondition(boolean condition, String message) {
		if (!condition) {
			coordinator.log(LogLevel.INTERNAL_ERROR, this, message);
		}
		return condition;
	}
}
