package dd.kms.hippodamus.coordinator.configuration;

import dd.kms.hippodamus.execution.configuration.ExecutionConfiguration;
import dd.kms.hippodamus.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.logging.LogLevel;
import dd.kms.hippodamus.logging.Logger;

import java.util.concurrent.ExecutorService;

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
	 *                          common {@link java.util.concurrent.ForkJoinPool}, whereas tasks of type
	 *                          {@code TaskType#IO} will be executed on a dedicated single-threaded
	 *                          {@code ExecutorService}.
	 * @param shutdownRequired	If set to true, then the specified {@code ExecutorService} will be shut down
	 *                          when the {@code ExecutionCoordinator} finishes.
	 */
	B executorService(int taskType, ExecutorService executorService, boolean shutdownRequired);

	/**
	 * Specifies the logger that is used to log received messages. If not specified, then the {@link dd.kms.hippodamus.logging.Loggers#NO_LOGGER}
	 * will be used.
	 */
	B logger(Logger logger);

	/**
	 * Specifies the minimum {@link LogLevel} that should be logged. Messages with a lower {@code LogLevel}
	 * will be swallowed. If not specified, then {@link LogLevel#STATE} will be used.
	 */
	B minimumLogLevel(LogLevel minimumLogLevel);

	// TODO: Ensure that the behavior for false is not to return null, but wait until result provided (or stopped or exception)
	/**
	 * Specifies whether accessing values of tasks that have not yet completed results in an exception. This
	 * value defaults to false.<br/>
	 * <br/>
	 * One of the main objectives of {@link ExecutionConfiguration}s is to handle dependencies between tasks. Tasks
	 * are not submitted to an {@link ExecutorService} unless all of its dependencies have completed. Hence,
	 * when a task accesses the value of a {@link dd.kms.hippodamus.handles.ResultHandle}, it should not have to
	 * wait until this value is provided.<br/>
	 * <br/>
	 * If a user aims at specifying all dependencies correctly for optimum coordination, then accessing the value of
	 * a handle of a task that has not yet completed indicates and error and should result in an exception. This can
	 * be achieved by setting this flag to {@code true}.
	 */
	B verifyDependencies(boolean verifyDependencies);

	ExecutionCoordinator build();
}
