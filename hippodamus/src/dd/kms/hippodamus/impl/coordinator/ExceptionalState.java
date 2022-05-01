package dd.kms.hippodamus.impl.coordinator;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.impl.exceptions.Exceptions;

class ExceptionalState
{
	/**
	 * Holds an exception that has been encountered internally or an exception that has been thrown by any of
	 * the tasks. It may be set from a different thread than the one the coordinator is running in.
	 * However, the coordinator will regularly check it
	 * <ul>
	 *     <li>when a new task is added and</li>
	 *     <li>when the coordinator is closing</li>
	 * </ul>
	 */
	private Throwable			exception;

	/**
	 * Flag used to decide whether the stored {@link #exception} is internal or not. Since internal exceptions
	 * are prioritized, the current exception will be overwritten if it is not internal, but the new one is.
	 */
	private boolean				isInternalException;

	/**
	 * This field is required to ensure that {@link ExecutionCoordinator#checkException()} does not throw an exception
	 * twice.<br>
	 * <br>
	 * <b>Necessity:</b> Assume that an exception is thrown inside the try-block due to a call of
	 * {@link ExecutionCoordinator#checkException()}. This will cause the {@link ExecutionCoordinator#close()}
	 * method to be called automatically, which also checks for an existing exception to be thrown by calling
	 * {@code checkException()}. If this method would throw the stored exception again, then we had two exceptions
	 * to be thrown. Such conflicts are automatically resolved by Java by calling {@link Throwable#addSuppressed(Throwable)}.
	 * However, this method fails if both exceptions are identical. In that case, we would obtain an
	 * {@link IllegalArgumentException} "Self-suppression not permitted" instead, which is not what we want.
	 */
	private boolean 			hasThrownException;

	/**
	 * This field is true if at any point one of the loggers threw an exception when logging. In that case,
	 * we do not try to log further messages to avoid further exceptions.
	 */
	private boolean				loggerFaulty;

	void checkException() {
		if (exception != null && !hasThrownException) {
			hasThrownException = true;
			Exceptions.throwUnchecked(exception);
		}
	}

	boolean setException(Throwable exception, boolean isInternalException) {
		boolean overwriteException = this.exception == null || !this.isInternalException && isInternalException;
		if (overwriteException) {
			this.exception = exception;
			this.isInternalException = isInternalException;
		}
		return overwriteException;
	}

	boolean isLoggerFaulty() {
		return loggerFaulty;
	}

	void onLoggerException(Throwable loggerException) {
		loggerFaulty = true;
		Throwable internalException = new CoordinatorException("Exception in logger: " + loggerException, loggerException);
		setException(internalException, true);
	}
}
