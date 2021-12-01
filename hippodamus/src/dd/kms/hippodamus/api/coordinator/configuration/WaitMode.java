package dd.kms.hippodamus.api.coordinator.configuration;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;

public enum WaitMode
{
	/**
	 * In this mode, the method {@link ExecutionCoordinator#close()} will wait until
	 * all tasks managed by the coordinator have terminated or have been stopped
	 * before being submitted.<br>
	 * <br>
	 * This mode may cost some extra time compared to {@link #UNTIL_TERMINATION_REQUESTED}
	 * because it will wait for running tasks to terminate. You should use it if you
	 * want to guarantee that the coordinator does not influence anything out of its
	 * scope (its try-block).<br>
	 * <br>
	 * Note that the {@code WaitMode} is only evaluated if the coordinator is stopped
	 * for some reason (manually, automatically due to an exception, or automatically
	 * due to short circuit evaluation).
	 */
	UNTIL_TERMINATION,

	/**
	 * In this mode, the method {@link ExecutionCoordinator#close()} will wait until
	 * all tasks managed by the coordinator <b>have been requested to stop</b>, have
	 * terminated, or have been stopped before being submitted.<br>
	 * <br>
	 * You should use this mode if you do not care that some of the tasks executed
	 * by the coordinator are still running when the coordinator is ready to terminate.<br>
	 * <br>
	 * Note that the {@code WaitMode} is only evaluated if the coordinator is stopped
	 * for some reason (manually, automatically due to an exception, or automatically
	 * due to short circuit evaluation).
	 */
	UNTIL_TERMINATION_REQUESTED
}
