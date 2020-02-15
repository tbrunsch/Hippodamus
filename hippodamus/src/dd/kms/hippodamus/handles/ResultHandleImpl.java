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
	private final ExecutorServiceWrapper				executorServiceWrapper;
	private final StoppableExceptionalCallable<T, ?>	callable;
	private final boolean								verifyDependencies;
	private final HandleState							state;

	private final List<Runnable>						completionListeners					= new ArrayList<>();
	private final List<Runnable>						exceptionListeners					= new ArrayList<>();

	/**
	 * This lock is released when the task terminates, either successfully or exceptionally, or
	 * is stopped. The {@link #join()}-method acquires this lock to ensure that it only returns after
	 * termination.<br/>
	 * <br/>
	 * Note that this lock must be released before calling any listener to avoid deadlocks: Listeners,
	 * in particular completion listeners, might indirectly call {@code join()}, e.g., by calling
	 * {@link ResultHandleImpl#get()}. This is why we always release the termination lock at the
	 * beginning of a method (cf. {@link #markAsCompleted()}, {@link #setException(Throwable)}, and
	 * {@link #stop()})<br/>
	 * <br/>
	 * In contrast to that, the coordinator's termination lock, which we obtain by calling
	 * {@link InternalCoordinator#getTerminationLock()}, must be released after calling any listener
	 * to ensure that the coordinator does not close before notifying all listeners. This is why we
	 * always release the coordinator's termination lock at the end of a method.
	 */
	private final Semaphore 							terminationLock						= new Semaphore(1);

	/**
	 * This field stores whether we have acquired the handle's {@link #terminationLock}.
	 * It will be used to prevent releasing the {@code terminationLock} multiple times.<br/>
	 * <br/>
	 * This field is only modified in synchronized blocks or in methods that rule out concurrency
	 * problems.
	 */
	private boolean										acquiredTerminationLock				= false;

	/**
	 * This field stores whether we have acquired the coordinator's termination lock, which we
	 * obtain by calling {@link InternalCoordinator#getTerminationLock()}. It will be used to
	 * prevent releasing the coordinator's termination lock multiple times. While releasing the
	 * handle's {@link #terminationLock} multiple times might be ok in the current implementation,
	 * releasing the coordinator's termination lock multiple times would be fatal. Since the lock
	 * is only counting the number of tasks (and not tracking the tasks) that currently hold it,
	 * releasing the termination lock multiple times is equivalent to a handle releasing the lock
	 * too early.<br/>
	 * <br/>
	 * This field is only modified in synchronized blocks or in methods that rule out concurrency
	 * problems.
	 */
	private boolean										acquiredCoordinatorTerminationLock	= false;

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

	ResultHandleImpl(InternalCoordinator coordinator, String taskName, ExecutorServiceWrapper executorServiceWrapper, StoppableExceptionalCallable<T, ?> callable, boolean verifyDependencies, boolean stopped) {
		this.coordinator = coordinator;
		this.taskName = taskName;
		this.executorServiceWrapper = executorServiceWrapper;
		this.callable = callable;
		this.verifyDependencies = verifyDependencies;
		this.state = new HandleState(false, stopped);

		if (!stopped) {
			acquireTerminationLocks();
		}
	}

	/**
	 * @return true if and only if no internal error occurred
	 */
	private boolean onStartExecution() {
		synchronized (coordinator) {
			String error =	!state.isFlagSet(StateFlag.SUBMITTED)	? "A task that has not been submitted cannot be executed" :
				state.getException() != null			? "A handle that has not yet been executed cannot have an exception" :
					state.isFlagSet(StateFlag.COMPLETED)	? "A handle that has not yet been executed cannot have completed"
						: null;
			if (error != null) {
				coordinator.log(LogLevel.INTERNAL_ERROR, this, error);
				releaseTerminationLock();
				releaseCoordinatorTerminationLock();
				return false;
			}
			state.setFlag(StateFlag.STARTED_EXECUTION);
			coordinator.log(LogLevel.STATE, this, StateFlag.STARTED_EXECUTION.getTransactionEndString());
			return true;
		}
	}

	private void markAsCompleted() {
		synchronized (coordinator) {
			/*
			 * The handle's termination lock must be released before calling the completion listeners
			 * to avoid a deadlock: Listeners might call join, which waits for the termination lock
			 * to be released.
			 */
			releaseTerminationLock();
			try {
				if (state.getException() != null) {
					coordinator.log(LogLevel.INTERNAL_ERROR, this, "A handle with an exception cannot have completed");
					return;
				}
				if (state.isFlagSet(StateFlag.COMPLETED)) {
					return;
				}
				addPendingFlag(StateFlag.COMPLETED);
				notifyListeners(completionListeners, "completion listener", coordinator::onCompletion);
				setPendingFlags();
			} finally {
				releaseCoordinatorTerminationLock();
			}
		}
	}

	private void setException(Throwable exception) {
		synchronized (coordinator) {
			// Release handle's termination lock before calling any listener to be consistent with markAsCompleted().
			releaseTerminationLock();
			try {
				if (state.getException() != null) {
					return;
				}
				if (state.isFlagSet(StateFlag.COMPLETED)) {
					coordinator.log(LogLevel.INTERNAL_ERROR, this, "A handle of a completed task cannot have an exception");
					return;
				}
				state.setException(exception);
				notifyListeners(exceptionListeners, "exception listener", coordinator::onException);
				setPendingFlags();
			} finally {
				releaseCoordinatorTerminationLock();
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
				taskHandle = executorServiceWrapper.submit(this::executeCallable);
			} finally {
				setPendingFlags();
			}
		}
	}

	@Override
	public final void stop() {
		synchronized (coordinator) {
			// First release handle's termination lock to be consistent with markAsCompleted().
			releaseTerminationLock();
			try {
				if (hasStopped()) {
					return;
				}
				addPendingFlag(StateFlag.STOPPED);
				try {
					coordinator.stopDependentHandles(this);
					if (state.isFlagSet(StateFlag.SUBMITTED)) {
						taskHandle.stop();
					}
				} finally {
					setPendingFlags();
				}
			} finally {
				if (isReleaseTerminationLockOnStop()) {
					releaseCoordinatorTerminationLock();
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
		if (hasStopped() || getException() != null) {
			return;
		}
		acquireLock(terminationLock, true);
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

	// TODO: Throw an exception if state.getException() != null?
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
			markAsCompleted();
		}
	}

	private T executeCallable() {
		// TODO: Abort execution if handle stopped?
		if (!onStartExecution()) {
			return null;
		}
		try {
			T result = callable.call(this::hasStopped);
			setResult(result);
			return result;
		} catch (Throwable throwable) {
			setException(throwable);
			return null;
		}
	}

	/*
	 * Termination Locks
	 */

	/**
	 * Ensure that this method is only called with locking the coordinator or at a
	 * point where concurrency problems regarding field {@code acquiredTerminationLock}
	 * can be ruled out.
	 */
	private void acquireTerminationLocks() {
		acquiredTerminationLock = acquireLock(terminationLock, false);
		if (acquiredTerminationLock) {
			/*
			 * Only acquire coordinator's termination lock if own termination lock could
			 * be acquired. Otherwise, the task will be stopped anyway.
			 */
			acquiredCoordinatorTerminationLock = acquireLock(coordinator.getTerminationLock(), false);
		}
		if (!acquiredTerminationLock || !acquiredCoordinatorTerminationLock) {
			/*
			 * Acquiring a lock can only fail if the thread has been interrupted. In that
			 * case, we stop the task.
			 */
			stop();
		}
	}

	private boolean acquireLock(Semaphore lock, boolean releaseAfterwards) {
		try {
			lock.acquire();
			return !releaseAfterwards;
		} catch (InterruptedException e) {
			return false;
		} finally {
			if (releaseAfterwards) {
				lock.release();
			}
		}
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	private void releaseTerminationLock() {
		coordinator.log(LogLevel.DEBUGGING, this, "Releasing handle's termination lock");
		acquiredTerminationLock = !releaseLock(terminationLock, acquiredTerminationLock);
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	private void releaseCoordinatorTerminationLock() {
		coordinator.log(LogLevel.DEBUGGING, this, "Releasing coordinator's termination lock");
		acquiredCoordinatorTerminationLock = !releaseLock(coordinator.getTerminationLock(), acquiredCoordinatorTerminationLock);
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	private boolean releaseLock(Semaphore lock, boolean acquiredLock) {
		if (acquiredLock) {
			lock.release();
		}
		return true;
	}

	/**
	 * Ensure that this method is only called with locking the coordinator.
	 */
	private boolean isReleaseTerminationLockOnStop() {
		/*
		 * Do not release the termination lock on stop if
		 *     (1) the coordinator should wait until all tasks have terminated (not only requested to terminate) and
		 *     (2) the task's execution has started.
		 * If both conditions are met, then the lock will be released on termination, either
		 * successfully in markAsCompleted() or exceptionally in setException().
		 */
		return !(coordinator.getWaitMode() == WaitMode.UNTIL_TERMINATION && state.isFlagSet(StateFlag.STARTED_EXECUTION));
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
