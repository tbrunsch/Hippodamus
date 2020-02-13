package dd.kms.hippodamus.execution;

import java.util.concurrent.Future;

class InternalTaskHandleImpl implements InternalTaskHandle
{
	private final Future<?>	future;

	InternalTaskHandleImpl(Future<?> future) {
		this.future = future;
	}

	@Override
	public void stop() {
		future.cancel(true);
	}
}
