package dd.kms.hippodamus.api.exceptions;

/**
 * This exception is thrown for every exceptional state that occur inside the coordinator
 * that is not caused by an exception in one of the tasks. Possible causes are:
 * <ul>
 *     <li>Internal errors within the coordinator</li>
 *     <li>
 *         Dependency verification is activated (see {@link dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder#verifyDependencies(boolean)})
 *         and one of the tasks tries to access a value of another task that has not yet completed. This indicates
 *         that the latter task has not been specified as dependency of the former task.
 *     </li>
 *     <li>
 *         An exception thrown by one of the completion or exception listeners (see
 *         {@link dd.kms.hippodamus.api.handles.Handle#onCompletion(Runnable)} and
 *         {@link dd.kms.hippodamus.api.handles.Handle#onException(Runnable)}).
 *     </li>
 *     <li>
 *         An exception thrown by one of the loggers registered via
 *         {@link dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder#logger(dd.kms.hippodamus.api.logging.Logger)}
 *     </li>
 * </ul>
 */
public class CoordinatorException extends RuntimeException
{
	public CoordinatorException(String message) {
		super(message);
	}

	public CoordinatorException(String message, Throwable cause) {
		super(message, cause);
	}
}
