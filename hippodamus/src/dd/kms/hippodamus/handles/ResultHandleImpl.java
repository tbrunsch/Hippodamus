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
	private final HandleState							state;

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

	/**
	 * Collects flags that will be set to finish a state change. The reason for this is that
	 * we want the flags to be updated after notifying listeners. However, there are listeners that
	 * also change a handle's state. This field ensures that the of the state changes happens in
	 * the original order.<br/>
	 * <br/>
	 * Reordering the state changes can be critical for example for short-circuit evaluation:
	 * A change to "completed" may trigger a "stop" for all handles via a completion listener,
	 * making the handle stop before it has set the "completed" flag. The {@code ExecutionCoordinator}
	 * may now close before the handle's "completed" flag is set.<br/>
	 */
	private final Queue<StateFlag> 						pendingFlags						= new ArrayDeque<>();

	private volatile InternalTaskHandle					taskHandle;
	private volatile T									result;

	/**
	 * This field is required to distinguish the case {@code result == null} because it has not yet
	 * been set from the case {@code result == null} because that was the actual result.
	 */
	private volatile boolean							resultIsSet;

	ResultHandleImpl(InternalCoordinator coordinator, String taskName, int id, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies, boolean stopped) {
		this.coordinator = coordinator;
		this.taskName = taskName;
		this.id = id;
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
		this.verifyDependencies = verifyDependencies;
		this.state = new HandleState(false, stopped);

		terminatedFlagForJoin = new AwaitableBoolean(new Semaphore(1));
		terminatedFlagForCoordinator = new AwaitableBoolean(coordinator.getTerminationLock());

		if (!stopped) {
			try {
				terminatedFlagForJoin.setFalse();
				terminatedFlagForCoordinator.setFalse();
			} catch (InterruptedException e) {
				stop();
				terminatedFlagForJoin.setTrue();
				terminatedFlagForCoordinator.setTrue();
			}
		}
	}

	/**
	 * @return true if task should be executed
	 */
	private boolean onStartExecution() {
		synchronized (coordinator) {
			String error =	!state.isFlagSet(StateFlag.SUBMITTED)	? "A task that has not been submitted cannot be executed" :
							state.getException() != null			? "A handle that has not yet been executed cannot have an exception" :
							state.isFlagSet(StateFlag.COMPLETED)	? "A handle that has not yet been executed cannot have completed"
						: null;
			if (error != null) {
				coordinator.log(LogLevel.INTERNAL_ERROR, this, error);
				assert state.isFlagSet(StateFlag.STOPPED);
			}
			if (state.isFlagSet(StateFlag.STOPPED)) {
				return false;
			}
			state.setFlag(StateFlag.STARTED_EXECUTION);
			coordinator.log(LogLevel.STATE, this, StateFlag.STARTED_EXECUTION.getTransactionEndString());
			return true;
		}
	}

	private void markAsCompleted() {
		synchronized (coordinator) {
			try {
				if (state.getException() != null) {
					coordinator.log(LogLevel.INTERNAL_ERROR, this, "A handle with an exception cannot have completed");
					return;
				}
				if (state.isFlagSet(StateFlag.COMPLETED)) {
					return;
				}
				addPendingFlag(StateFlag.COMPLETED);

				/*
				 * The terminated flag awaited in the join-method must be set to true before calling the
				 * completion listeners to avoid a deadlock: Listeners might call join(), which waits
				 * for the flag to be set.
				 */
				setTerminatedFlagForJoin();

				notifyListeners(completionListeners, "completion listener", coordinator::onCompletion);
				executorServiceWrapper.onTaskCompleted();
				setPendingFlags();
			} finally {
				setTerminatedFlagForCoordinator();
			}
		}
	}

	private void setException(Throwable exception) {
		synchronized (coordinator) {
			try {
				if (state.getException() != null) {
					return;
				}
				if (state.isFlagSet(StateFlag.COMPLETED)) {
					coordinator.log(LogLevel.INTERNAL_ERROR, this, "A handle of a completed task cannot have an exception");
					return;
				}
				state.setException(exception);
				setTerminatedFlagForJoin();
				notifyListeners(exceptionListeners, "exception listener", coordinator::onException);
				setPendingFlags();
			} finally {
				setTerminatedFlagForCoordinator();
			}
		}
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
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
			coordinator.log(LogLevel.INTERNAL_ERROR, this, message);
		}
	}

	@Override
	public void submit() {
		synchronized (coordinator) {
			if (state.isFlagSet(StateFlag.STOPPED) || state.isFlagSet(StateFlag.SUBMITTED)) {
				return;
			}
			addPendingFlag(StateFlag.SUBMITTED);
			try {
				taskHandle = executorServiceWrapper.submit(id, this::executeCallable);
			} finally {
				setPendingFlags();
			}
		}
	}

	@Override
	public final void stop() {
		synchronized (coordinator) {
			try {
				if (hasStopped()) {
					return;
				}
				addPendingFlag(StateFlag.STOPPED);
				setTerminatedFlagForJoin();
				try {
					coordinator.stopDependentHandles(this);
					if (state.isFlagSet(StateFlag.SUBMITTED)) {
						taskHandle.stop();
					}
				} finally {
					setPendingFlags();
				}
			} finally {
				boolean coordinatorMustWaitForThisHandle = coordinator.getWaitMode() == WaitMode.UNTIL_TERMINATION && state.isFlagSet(StateFlag.STARTED_EXECUTION);
				if (!coordinatorMustWaitForThisHandle) {
					setTerminatedFlagForCoordinator();
				}
			}
		}
	}

	@Override
	public void join() {
		if (hasCompleted()) {
			return;
		}
		if (verifyDependencies) {
			synchronized (coordinator) {
				coordinator.log(LogLevel.INTERNAL_ERROR, this, "Waiting for a handle that has not yet completed. Did you forget to specify that handle as dependency?");
			}
			return;
		}
		if (resultIsSet) {
			return;
		}
		checkStoppedHandle();
		terminatedFlagForJoin.waitUntilTrue();
		if (!resultIsSet) {
			throw new TaskStoppedException(taskName);
		}
	}

	private void checkStoppedHandle() {
		if (getException() != null || hasStopped()) {
			throw new TaskStoppedException(taskName);
		}
	}

	@Override
	public boolean hasCompleted() {
		return state.isFlagSet(StateFlag.COMPLETED);
	}

	@Override
	public final boolean hasStopped() {
		return state.isFlagSet(StateFlag.STOPPED);
	}

	@Override
	public @Nullable Throwable getException() {
		return state.getException();
	}

	@Override
	public String getTaskName() {
		return taskName;
	}

	@Override
	public void onCompletion(Runnable listener) {
		synchronized (coordinator) {
			completionListeners.add(listener);
			if (state.isFlagSet(StateFlag.COMPLETED)) {
				// only run this listener; other listeners have already been notified
				notifyListeners(Collections.singletonList(listener), "completion listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	@Override
	public void onException(Runnable listener) {
		synchronized (coordinator) {
			exceptionListeners.add(listener);
			Throwable exception = state.getException();
			if (exception != null) {
				// only inform this handler; other handlers have already been notified
				notifyListeners(Collections.singletonList(listener), "exception listener", NO_HANDLE_CONSUMER);
			}
		}
	}

	@Override
	public final InternalCoordinator getExecutionCoordinator() {
		return coordinator;
	}

	@Override
	public T get() {
		join();
		return result;
	}

	private void setResult(T value) {
		synchronized (state) {
			if (state.isFlagSet(StateFlag.COMPLETED) || state.getException() != null) {
				return;
			}
			result = value;
			resultIsSet = true;
			markAsCompleted();
		}
	}

	private void executeCallable() {
		if (!onStartExecution()) {
			return;
		}
		try {
			T result = callable.call(this::hasStopped);
			setResult(result);
		} catch (TaskStoppedException e) {
			setTerminatedFlagForJoin();
			setTerminatedFlagForCoordinator();
			stop();
		} catch (Throwable throwable) {
			setException(throwable);
		}
	}

	/*
	 * Terminated Flags
	 */
	private void setTerminatedFlagForJoin() {
		coordinator.log(LogLevel.DEBUGGING, this, "Notifying waiting threads about termination...");
		terminatedFlagForJoin.setTrue();
	}

	private void setTerminatedFlagForCoordinator() {
		coordinator.log(LogLevel.DEBUGGING, this, "Notifying coordinator about termination...");
		terminatedFlagForCoordinator.setTrue();
	}

	/*
	 * Pending Flags
	 */

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	private void addPendingFlag(StateFlag flag) {
		coordinator.log(LogLevel.DEBUGGING, this, flag.getTransactionBeginString());
		pendingFlags.add(flag);
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	private void setPendingFlags() {
		StateFlag flag;
		while ((flag = pendingFlags.poll()) != null) {
			state.setFlag(flag);
			coordinator.log(LogLevel.STATE, this, flag.getTransactionEndString());
		}
	}
}
