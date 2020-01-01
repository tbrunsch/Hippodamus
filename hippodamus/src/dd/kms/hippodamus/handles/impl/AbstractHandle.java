package dd.kms.hippodamus.handles.impl;

import dd.kms.hippodamus.coordinator.TaskCoordinator;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.logging.LogLevel;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

abstract class AbstractHandle implements Handle
{
	private final TaskCoordinator				coordinator;
	private final Deque<Runnable> 				completionListeners	= new LinkedList<>();
	private final Deque<Consumer<Throwable>>	exceptionHandlers	= new LinkedList<>();
	final HandleState							state;

	/**
	 * Collects flags that will be set to finish a state change. The reason for this is that
	 * it is not trivial when to update the flags state, before or after calling the listeners.
	 * Since the {@link TaskCoordinator} is polling the "stopped" and "completed" flags, these
	 * flags must be set after calling the listeners because otherwise the {@code TaskCoordinator}
	 * may close before the listeners have been informed. On the other hand, one listener might
	 * also change a handle's flag. This will reorder the state changes and can be critical
	 * for example for short-circuit evaluation: A change to "completed" may trigger a "stop"
	 * for all handles via a completion listener, making the handle stop before it has set the
	 * "completed" flag. The {@code TaskCoordinator} may now close before the handle's "completed"
	 * flag is set.<br/>
	 * As a consequence, we need this field to maintain the order of the flags that are set.
	 */
	private final List<StateFlag>			pendingFlags		= new ArrayList<>();

	AbstractHandle(TaskCoordinator coordinator, HandleState state) {
		this.coordinator = coordinator;
		this.state = new HandleState(state);
	}

	abstract void doSubmit();
	abstract void doStop();

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
			try {
				completionListeners.forEach(Runnable::run);
			} catch (Exception e) {
				// TODO
				e.printStackTrace();
			} finally {
				setPendingFlags();
			}
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
			// TODO: Stopping the tasks is responsibility of the task coordintor => remove this line
			addPendingFlag(StateFlag.STOPPED);
			try {
				exceptionHandlers.forEach(handler -> handler.accept(exception));
			} catch (Exception e) {
				// TODO
				e.printStackTrace();
			} finally {
				state.setException(exception);
				setPendingFlags();
			}
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
			} catch (Exception e) {
				// TODO
				e.printStackTrace();
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
			} catch (Exception e) {
				// TODO
				e.printStackTrace();
			} finally {
				setPendingFlags();
			}
		}
	}

	@Override
	public boolean hasCompleted() {
		return state.isFlagSet(StateFlag.COMPLETED);
	}

	/**
	 * For internal use only. Ensure that this method is only called with locking the {@code coordinator}.
	 */
	boolean isCompleting() {
		return pendingFlags.contains(StateFlag.COMPLETED);
	}

	@Override
	public final boolean hasStopped() {
		return state.isFlagSet(StateFlag.STOPPED);
	}

	@Override
	public void onCompletion(Runnable listener) {
		synchronized (coordinator) {
			/*
			 * This method will be called by BasicTaskCoordinator first, then possibly by derived classes, and
			 * then possibly by users. We want the listeners to be called in reverse order. Among others for the
			 * reason, that submitting dependent tasks may be prevented by the other listeners (e.g., due to
			 * short circuit evaluation exploited by AggregatingTaskCoordinatorImpl).
			 */
			completionListeners.addFirst(listener);
			if (state.isFlagSet(StateFlag.COMPLETED)) {
				// only run this listener; other listeners have already been notified
				listener.run();
			}
		}
	}

	@Override
	public void onException(Consumer<Throwable> exceptionHandler) {
		synchronized (coordinator) {
			/*
			 * Maintain collection of exception handlers in reverse call order to be consistent with order
			 * of completion listeners.
			 */
			exceptionHandlers.addFirst(exceptionHandler);
			Throwable exception = state.getException();
			if (exception != null) {
				// only inform this handler; other handlers have already been notified
				exceptionHandler.accept(exception);
			}
		}
	}

	@Override
	public final TaskCoordinator getTaskCoordinator() {
		return coordinator;
	}

	/*
	 * Pending Flags
	 */
	private void addPendingFlag(StateFlag flag) {
		coordinator.log(LogLevel.STATE, this, flag.getTransactionBeginString());
		pendingFlags.add(flag);
	}

	private void setPendingFlags() {
		for (StateFlag flag : pendingFlags) {
			state.setFlag(flag);
			coordinator.log(LogLevel.STATE, this, flag.getTransactionEndString());
		}
		pendingFlags.clear();
	}
}
