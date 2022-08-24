package dd.kms.hippodamus.resources;

import dd.kms.hippodamus.api.execution.ExecutionController;

class UnlimitedCountableResource implements CountableResource
{
	static final CountableResource	RESOURCE	= new UnlimitedCountableResource();
	@Override
	public ExecutionController getShare(long size) {
		return UnlimitedCountableResourceShare.SHARE;
	}

	private static class UnlimitedCountableResourceShare implements ExecutionController
	{
		static final ExecutionController	SHARE	= new UnlimitedCountableResourceShare();

		@Override
		public boolean permitExecution(Runnable submitLaterRunnable) {
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
}
