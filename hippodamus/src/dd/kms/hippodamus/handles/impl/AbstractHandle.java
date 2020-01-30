package dd.kms.hippodamus.handles.impl;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.logging.LogLevel;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Consumer;

abstract class AbstractHandle implements Handle
{
	private static final Consumer<Handle>	NO_HANDLE_CONSUMER = handle -> {};

	private final InternalCoordinator	coordinator;
	private final List<Runnable> 		completionListeners	= new ArrayList<>();
	private final List<Runnable>		exceptionListeners	= new ArrayList<>();
	final HandleState					state;
	private final boolean				verifyDependencies;

	/**
	 * Collects flags that will be set to finish a state change. The reason for this is that
	 * it is not trivial when to update the flags state, before or after calling the listeners.
	 * Since the {@link ExecutionCoordinator} is polling the "stopped" and "completed" flags, these
	 * flags must be set after calling the listeners because otherwise the {@code ExecutionCoordinator}
	 * may close before the listeners have been informed. On the other hand, one listener might
	 * also change a handle's flag. This will reorder the state changes and can be critical
	 * for example for short-circuit evaluation: A change to "completed" may trigger a "stop"
	 * for all handles via a completion listener, making the handle stop before it has set the
	 * "completed" flag. The {@code ExecutionCoordinator} may now close before the handle's "completed"
	 * flag is set.<br/>
	 * As a consequence, we need this field to maintain the order of the flags that are set.
	 */
	private final Queue<StateFlag>		pendingFlags		= new ArrayDeque<>();

	AbstractHandle(InternalCoordinator coordinator, HandleState state, boolean verifyDependencies) {
		this.coordinator = coordinator;
		this.state = new HandleState(state);
		this.verifyDependencies = verifyDependencies;
	}

	abstract void doSubmit();
	abstract void doStop();
	abstract boolean doWaitForFuture() throws Throwable;

	void markAsCompleted() {
		synchronized (coordinator) {
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
		}
	}

	void setException(Throwable exception) {
		synchronized (coordinator) {
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
		}
	}

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
					break;
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
				doSubmit();
			} finally {
				setPendingFlags();
			}
		}
	}

	@Override
	public final void stop() {
		synchronized (coordinator) {
			if (hasStopped()) {
				return;
			}
			addPendingFlag(StateFlag.STOPPED);
			try {
				coordinator.stopDependentHandles(this);
				if (state.isFlagSet(StateFlag.SUBMITTED)) {
					doStop();
				}
			} finally {
				setPendingFlags();
			}
		}
	}

	@Override
	public void join() {
		if (hasCompleted() || isCompleting()) {
			return;
		}
		if (verifyDependencies) {
			coordinator.log(LogLevel.INTERNAL_ERROR, this, "Waiting for a handle that has not yet completed. Did you forget to specify that handle as dependency?");
			return;
		}
		while (true) {
			try {
				if (doWaitForFuture()) {
					return;
				}
			} catch (Throwable t) {
				setException(t);
				return;
			}
			if (hasCompleted() || isCompleting()) {
				return;
			}
			if (hasStopped()) {
				return;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				stop();
				return;
			}
		}
	}

	@Override
	public boolean hasCompleted() {
		return state.isFlagSet(StateFlag.COMPLETED);
	}

	private boolean isCompleting() {
		synchronized (coordinator) {
			return pendingFlags.contains(StateFlag.COMPLETED);
		}
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
		return coordinator.getTaskName(this);
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

	/*
	 * Pending Flags
	 */
	/**
	 * Ensure that this method is only called with locking the {@code coordinator}.
	 */
	private void addPendingFlag(StateFlag flag) {
		coordinator.log(LogLevel.DEBUGGING, this, flag.getTransactionBeginString());
		pendingFlags.add(flag);
	}

	/**
	 * Ensure that this method is only called with locking the {@code coordinator}.
	 */
	private void setPendingFlags() {
		StateFlag flag;
		while ((flag = pendingFlags.poll()) != null) {
			state.setFlag(flag);
			coordinator.log(LogLevel.STATE, this, flag.getTransactionEndString());
		}
	}
}
