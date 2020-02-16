package dd.kms.hippodamus.execution;

import java.util.concurrent.Future;
import java.util.function.Supplier;

class InternalTaskHandleImpl implements InternalTaskHandle
{
	private final Supplier<Future<?>>	submitter;
	private Future<?>					future;

	InternalTaskHandleImpl(Supplier<Future<?>> submitter) {
		this.submitter = submitter;
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
