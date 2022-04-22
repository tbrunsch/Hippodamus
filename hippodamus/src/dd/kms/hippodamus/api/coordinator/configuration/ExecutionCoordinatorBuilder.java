package dd.kms.hippodamus.api.coordinator.configuration;

import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.exceptions.CoordinatorException;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;
import dd.kms.hippodamus.api.handles.ResultHandle;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * Builder for an {@link ExecutionCoordinator} that allows specifying
 * <ul>
 *     <li>the {@link ExecutorService} for tasks of a certain type,</li>
 *     <li>the maximum parallelism,</li>
 *     <li>whether to verify the specified dependencies,</li>
 *     <li>how long the {@code ExecutionCoordinator}'s {@code close()} method waits, and</li>
 *     <li>a {@link Logger} and the minimum log level</li>
 * </ul>
 */
public interface ExecutionCoordinatorBuilder
{
	/**
	 * Specify an {@link ExecutorService} for a certain type of task and whether it has to be
	 * shut down when the {@link ExecutionCoordinator} finishes.
	 *
	 * @param taskType			The type of a task. This may either be {@link TaskType#COMPUTATIONAL},
	 *                          {@link TaskType#BLOCKING}, or any custom {@link TaskType} with a
	 *                          non-negative id (cf. {@link TaskType#create(int)}, {@link ExecutionCoordinator#configure()},
	 *                          and {@link ExecutionConfigurationBuilder#taskType(TaskType)}.
	 * @param executorService	The {@code ExecutorService} to be used for the specified type of task.
	 *                          By default, tasks of type {@code TaskType.COMPUTATIONAL} will be executed on the
	 *                          common {@link ForkJoinPool}, whereas tasks of type {@code TaskType.BLOCKING} will
	 *                          be executed on a dedicated single-threaded
	 *                          {@code ExecutorService}.
	 * @param shutdownRequired	If set to true, then the specified {@code ExecutorService} will be shut down
	 *                          when the {@code ExecutionCoordinator} finishes.
	 */
	ExecutionCoordinatorBuilder executorService(TaskType taskType, ExecutorService executorService, boolean shutdownRequired);

	/**
	 * Specify the maximum number of tasks of a certain type that are processed by their dedicated
	 * {@link ExecutorService} at any point in time.<br>
	 * <br>
	 * <b>Remarks</b>:
	 * <ul>
	 *     <li>
	 *         It is not guaranteed that the {@code ExecutorService} can also execute so many tasks
	 * 	       in parallel.
	 *     </li>
	 *     <li>
	 *         Limiting the maximum parallelism can result in a <b>deadlock if not all task dependencies
	 *         are specified correctly</b> because tasks might be submitted to their {@code ExecutorService}
	 *         although tasks they depend on have not yet been submitted. Due to the maximum parallelism
	 *         it is possible that these dependencies never get submitted because the submitted tasks
	 *         do not terminate.
	 *     </li>
	 * </ul>
	 *
	 * @param taskType			The type of a task. This may either be {@link TaskType#COMPUTATIONAL},
	 *                          {@link TaskType#BLOCKING}, or any custom {@link TaskType} with a
	 *                          non-negative id (cf. {@link TaskType#create(int)}, {@link ExecutionCoordinator#configure()},
	 *                          and {@link ExecutionConfigurationBuilder#taskType(TaskType)}.
	 * @param maxParallelism    The maximum number of tasks processed by the dedicated {@code ExecutorService}.
	 *
	 * @throws IllegalArgumentException if {@code maxParallelism} is not positive.
	 */
	ExecutionCoordinatorBuilder maximumParallelism(TaskType taskType, int maxParallelism);

	/**
	 * Specifies the logger that is used to log received messages. If not specified, then nothing will be logged.
	 */
	ExecutionCoordinatorBuilder logger(Logger logger);

	/**
	 * Specifies the minimum {@link LogLevel} that should be logged. Messages with a lower {@code LogLevel}
	 * will be swallowed. If not specified, then {@link LogLevel#STATE} will be used.
	 */
	ExecutionCoordinatorBuilder minimumLogLevel(LogLevel minimumLogLevel);

	/**
	 * Specifies whether accessing values of tasks that have not yet completed results in an exception. This
	 * value defaults to false.<br>
	 * <br>
	 * Hippodamus handles dependencies between tasks. Tasks are not submitted to an {@link ExecutorService} unless
	 * all of its dependencies have completed. Hence, when a task accesses the value of a {@link ResultHandle}, it
	 * should not have to wait until this value is provided.<br>
	 * <br>
	 * If a user aims at specifying all dependencies correctly for optimum coordination, then accessing the value of
	 * a handle of a task that has not yet completed indicates and error and should result in an exception. This can
	 * be achieved by setting this flag to {@code true}. In this case, a {@link CoordinatorException} will be
	 * thrown in the {@link ExecutionCoordinator}'s thread that should be caught after the {@code ExecutionCoordinator}'s
	 * try-block.
	 */
	ExecutionCoordinatorBuilder verifyDependencies(boolean verifyDependencies);

	/**
	 * Specifies the {@link ExecutionCoordinator}'s waiting behavior. Two modes are supported:
	 * <ul>
	 *     <li>
	 *         {@link WaitMode#UNTIL_TERMINATION} (default): The coordinator's {@code close()} method will block until
	 *         all tasks managed by the coordinator have terminated or have been stopped before being submitted. No task will
	 *         be running when the coordinator terminates.
	 *     </li>
	 *     <li>
	 *         {@link WaitMode#UNTIL_TERMINATION_REQUESTED}: The coordinator's {@code close()} method will block until all
	 *         tasks managed by the coordinator <b>have been requested to terminate</b>, have terminated, or have been
	 *         stopped before being submitted. Some tasks might still be running when the coordinator terminates.
	 *     </li>
	 * </ul>
	 */
	ExecutionCoordinatorBuilder waitMode(WaitMode waitMode);

	ExecutionCoordinator build();
}
