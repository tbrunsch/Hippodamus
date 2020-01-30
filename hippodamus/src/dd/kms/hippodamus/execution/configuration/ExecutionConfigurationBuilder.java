package dd.kms.hippodamus.execution.configuration;

import dd.kms.hippodamus.coordinator.Coordinators;
import dd.kms.hippodamus.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.coordinator.TaskType;
import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.execution.ExecutionManager;
import dd.kms.hippodamus.handles.Handle;

import java.util.Collection;
import java.util.concurrent.ExecutorService;

public interface ExecutionConfigurationBuilder<B extends ExecutionConfigurationBuilder> extends ExecutionManager
{
	/**
	 * Specify a name for the task. This is particularly helpful for debugging. If you do not
	 * specify a name, then the tasks will simply be enumerated (Task 1, Task 2, ...).
	 */
	B name(String name);

	/**
	 * Specifies the type of the task. Based on the type the {@link ExecutionCoordinator} decides which {@link ExecutorService}
	 * the task should be submitted to.<br/>
	 * <br/>
	 * You can use predefined task types {@link TaskType#REGULAR} (default) and {@link TaskType#IO} or define
	 * custom types and register {@code ExecutorService}s for these types (see {@link Coordinators#} and {@link ExecutionCoordinatorBuilder#executorService(int, ExecutorService, boolean)}).
	 */
	B taskType(int type);

	/**
	 * Specifies the tasks dependencies.<br/>
	 * <br/>
	 * The task will not be submitted to the {@link ExecutorService} unless all dependencies have completed.
	 */
	B dependencies(Handle... dependencies);

	/**
	 * Specifies the tasks dependencies.
	 *
	 * @see ExecutionConfigurationBuilder#dependencies(Handle...)
	 */
	B dependencies(Collection<Handle> dependencies);
}
