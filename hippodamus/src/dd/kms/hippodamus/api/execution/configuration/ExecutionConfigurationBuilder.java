package dd.kms.hippodamus.api.execution.configuration;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.execution.ExecutionManager;
import dd.kms.hippodamus.api.handles.Handle;

import java.util.Collection;
import java.util.concurrent.ExecutorService;

public interface ExecutionConfigurationBuilder extends ExecutionManager
{
	/**
	 * Specify a name for the task. This is particularly helpful for debugging. If you do not
	 * specify a name, then the tasks will simply be enumerated (Task 1, Task 2, ...).
	 */
	ExecutionConfigurationBuilder name(String name);

	/**
	 * Specifies the type of the task. Based on the type the {@link ExecutionCoordinator} decides which {@link ExecutorService}
	 * the task should be submitted to.<br>
	 * <br>
	 * You can use predefined task types {@link TaskType#REGULAR} (default) and {@link TaskType#IO} or define
	 * custom types and register {@code ExecutorService}s for these types (see {@link Coordinators#} and {@link ExecutionCoordinatorBuilder#executorService(int, ExecutorService, boolean)}).
	 *
	 * @throws IllegalArgumentException if no {@code ExecutorService} has been specified for the task type
	 */
	ExecutionConfigurationBuilder taskType(int type);

	/**
	 * Specifies the tasks dependencies.<br>
	 * <br>
	 * The task will not be submitted to the {@link ExecutorService} unless all dependencies have completed.
	 *
	 * @throws IllegalArgumentException if one of the specified dependencies belongs to a different {@link ExecutionCoordinator}
	 */
	ExecutionConfigurationBuilder dependencies(Handle... dependencies);

	/**
	 * Specifies the tasks dependencies.
	 *
	 * @see ExecutionConfigurationBuilder#dependencies(Handle...)
	 */
	ExecutionConfigurationBuilder dependencies(Collection<Handle> dependencies);
}
