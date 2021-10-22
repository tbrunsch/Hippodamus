package dd.kms.hippodamus.execution;

import dd.kms.hippodamus.coordinator.InternalCoordinator;
import dd.kms.hippodamus.resources.ResourceShare;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

class InternalTaskHandleImpl implements InternalTaskHandle
{
	private final int										id;
	private final Runnable									runnable;
	private final List<ResourceShare<?>>					requiredResourceShares;
	private final InternalCoordinator						coordinator;
	private final Function<InternalTaskHandle, Future<?>>	submitter;
	private final Consumer<InternalTaskHandle>				onTaskCompleted;

	private Future<?>	future;

	InternalTaskHandleImpl(int id, Runnable runnable, List<ResourceShare<?>> requiredResourceShares, InternalCoordinator coordinator, Function<InternalTaskHandle, Future<?>> submitter, Consumer<InternalTaskHandle> onTaskCompleted) {
		this.id = id;
		this.runnable = runnable;
		this.requiredResourceShares = requiredResourceShares;
		this.coordinator = coordinator;
		this.submitter = submitter;
		this.onTaskCompleted = onTaskCompleted;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public List<ResourceShare<?>> getRequiredResourceShares() {
		return requiredResourceShares;
	}

	@Override
	public InternalCoordinator getCoordinator() {
		return coordinator;
	}

	@Override
	public void submit() {
		if (future == null) {
			future = submitter.apply(this);
		}
	}

	@Override
	public void stop() {
		if (future != null) {
			future.cancel(true);
		}
	}

	@Override
	public void run() {
		runnable.run();
		onTaskCompleted.accept(this);
	}
}
