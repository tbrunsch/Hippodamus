package dd.kms.hippodamus.impl.execution;

import dd.kms.hippodamus.api.execution.ExecutionController;

public class NoExecutionController implements ExecutionController
{
	public static ExecutionController	CONTROLLER	= new NoExecutionController();

	/* singleton */
	private NoExecutionController() {}

	@Override
	public boolean permitExecution(Runnable submitLaterRunnable) {
		// execute task immediately
		return true;
	}

	@Override
	public void stop() {
		/* nothing to do */
	}

	@Override
	public void finishedExecution(boolean finishedSuccessfully) {
		/* nothing to do */
	}
}
