package dd.kms.hippodamus.coordinator.configuration;

import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.exceptions.CoordinatorException;
import dd.kms.hippodamus.execution.configuration.ExecutionConfiguration;
import dd.kms.hippodamus.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.handles.ResultHandle;
import dd.kms.hippodamus.logging.LogLevel;
import dd.kms.hippodamus.logging.Logger;
import dd.kms.hippodamus.logging.Loggers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

public interface ExecutionCoordinatorBuilder<B extends ExecutionCoordinatorBuilder<B>>
{
	/**
	 * Specify an {@link ExecutorService} for a certain type of task and whether it has to be
	 * shut down when the {@link ExecutionCoordinator} finishes.
	 *
	 * @param taskType			The type of a task. This may either be {@link TaskType#REGULAR} for regular
	 *                          tasks, {@link TaskType#IO} for IO tasks or any non-negative integer for
	 *                          a custom type of task (cf. {@link ExecutionCoordinator#configure()} and
	 *                          {@link ExecutionConfigurationBuilder#taskType(int)}.
	 * @param executorService	The {@code ExecutorService} to be used for the specified type of task.
	 *                          By default, tasks of type {@code TaskType#REGULAR} will be executed on the
	 *                          common {@link ForkJoinPool}, whereas tasks of type
	 *                          {@code TaskType#IO} will be executed on a dedicated single-threaded
	 *                          {@code ExecutorService}.
	 * @param shutdownRequired	If set to true, then the specified {@code ExecutorService} will be shut down
	 *                          when the {@code ExecutionCoordinator} finishes.
	 */
	B executorService(int taskType, ExecutorService executorService, boolean shutdownRequired);

	/**
	 * Specifies the logger that is used to log received messages. If not specified, then the {@link Loggers#NO_LOGGER}
	 * will be used.
	 */
	B logger(Logger logger);

	/**
	 * Specifies the minimum {@link LogLevel} that should be logged. Messages with a lower {@code LogLevel}
	 * will be swallowed. If not specified, then {@link LogLevel#STATE} will be used.
	 */
	B minimumLogLevel(LogLevel minimumLogLevel);

	/**
	 * Specifies whether accessing values of tasks that have not yet completed results in an exception. This
	 * value defaults to false.<br/>
	 * <br/>
	 * One of the main objectives of {@link ExecutionConfiguration}s is to handle dependencies between tasks. Tasks
	 * are not submitted to an {@link ExecutorService} unless all of its dependencies have completed. Hence,
	 * when a task accesses the value of a {@link ResultHandle}, it should not have to
	 * wait until this value is provided.<br/>
	 * <br/>
	 * If a user aims at specifying all dependencies correctly for optimum coordination, then accessing the value of
	 * a handle of a task that has not yet completed indicates and error and should result in an exception. This can
	 * be achieved by setting this flag to {@code true}. In this case, a {@link CoordinatorException} will be
	 * thrown in the {@link ExecutionCoordinator}'s thread that should be caught after the {@code ExecutionCoordinator}'s
	 * try-block.
	 */
	B verifyDependencies(boolean verifyDependencies);

	/**
	 * Specifies the {@link ExecutionCoordinator}'s waiting behavior. Two modes are supported:
	 * <ul>
	 *     <li>
	 *         {@link WaitMode#UNTIL_TERMINATION_REQUESTED} (default): The coordinator's {@code close()} method will
	 *         block until all tasks managed by the coordinator <b>have been requested to terminate</b>, have terminated,
	 *         or have been stopped before being submitted. Some tasks might still be running when the coordinator terminates,
	 *         but they are not relevant for the result.
	 *     </li>
	 *     <li>
	 *         {@link WaitMode#UNTIL_TERMINATION} (default): The coordinator's {@code close()} method will block until
	 *         all tasks managed by the coordinator have terminated or have been stopped before being submitted. No task will
	 *         be running when the coordinator terminates.
	 *     </li>
	 * </ul>
	 */
	B waitMode(WaitMode waitMode);

	ExecutionCoordinator build();
}
