package dd.kms.hippodamus.impl.execution;

import java.util.concurrent.Future;
import java.util.function.Supplier;

class InternalTaskHandleImpl implements InternalTaskHandle
{
	private final int					id;
	private final Supplier<Future<?>>	submitter;
	private Future<?>					future;

	InternalTaskHandleImpl(int id, Supplier<Future<?>> submitter) {
		this.id = id;
		this.submitter = submitter;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void submit() {
		if (future == null) {
			future = submitter.get();
		}
	}

	@Override
	public void stop() {
		if (future != null) {
			future.cancel(true);
		}
	}
}
