package dd.kms.hippodamus.api.execution.configuration;

import dd.kms.hippodamus.api.coordinator.Coordinators;
import dd.kms.hippodamus.api.coordinator.ExecutionCoordinator;
import dd.kms.hippodamus.api.coordinator.TaskType;
import dd.kms.hippodamus.api.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.ExecutionManager;
import dd.kms.hippodamus.api.handles.Handle;
import dd.kms.hippodamus.api.resources.Resource;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Use this interface to specify information about a task and how it will be executed. You can specify
 * <ul>
 *     <li>the task's name,</li>
 *     <li>the task's type, and</li>
 *     <li>other tasks this task depends on and which have to be executed before this task.</li>
 * </ul>
 * Call {@link dd.kms.hippodamus.api.coordinator.ExecutionCoordinator#configure()} to create
 * this builder for a task.
 */
public interface ExecutionConfigurationBuilder extends ExecutionManager
{
	/**
	 * Specify a name for the task. This is particularly helpful for debugging. If you do not
	 * specify a name, then the tasks will simply be enumerated (Task 1, Task 2, ...).
	 */
	ExecutionConfigurationBuilder name(String name);

	/**
	 * Specifies the type of the task. Based on the type the {@link dd.kms.hippodamus.api.coordinator.ExecutionCoordinator}
	 * decides which {@link ExecutorService} the task should be submitted to.<br>
	 * <br>
	 * You can use predefined task types {@link TaskType#COMPUTATIONAL} (default) and {@link TaskType#BLOCKING} or define
	 * custom types and register {@code ExecutorService}s for these types (see {@link TaskType#create(int)},
	 * {@link Coordinators#configureExecutionCoordinator()}, and {@link ExecutionCoordinatorBuilder#executorService(TaskType, ExecutorService, boolean)}).
	 *
	 * @throws IllegalArgumentException if no {@code ExecutorService} has been specified for the task type
	 */
	ExecutionConfigurationBuilder taskType(TaskType type);

	/**
	 * Specifies the tasks dependencies.<br>
	 * <br>
	 * The task will not be submitted to the {@link ExecutorService} unless all dependencies have completed.
	 *
	 * @throws IllegalArgumentException if one of the specified dependencies belongs to a different {@link ExecutionCoordinator}
	 */
	ExecutionConfigurationBuilder dependencies(Handle... dependencies);

	/**
	 * Specifies the task's dependencies.
	 *
	 * @see ExecutionConfigurationBuilder#dependencies(Handle...)
	 */
	ExecutionConfigurationBuilder dependencies(Collection<? extends Handle> dependencies);

	/**
	 * Specifies a resource the task requires and what/how much of it it requires. If the underlying {@link ExecutorService}
	 * schedules the task for execution, but the required resource is currently not available, then the resource
	 * postpones the tasks execution and resubmits it at a later point in time.<br>
	 * <br>
	 * Note that you can call the method multiple times when the task requires multiple resources.
	 */
	<T> ExecutionConfigurationBuilder requiredResource(Resource<T> resource, Supplier<T> resourceShareSupplier);

	/**
	 * The call {@link ExecutionCoordinator#execute(ExceptionalRunnable)} returns the {@link Handle} associated with the
	 * specified task. However, between the generation of the handle and when {@code execute()} returns the handle
	 * several things might happen:
	 * <ul>
	 *     <li>The logger might already send log messages referring to this handle.</li>
	 *     <li>The task might already start executing.</li>
	 *     <li>The task might even have already finished.</li>
	 * </ul>
	 * If you want to ensure that the coordinator's thread is aware of this handle in these scenarios, then you can call
	 * this method to register a consumer for this handle. This consumer will be called immediately after the handle
	 * has been created.
	 */
	ExecutionConfigurationBuilder onHandleCreation(Consumer<Handle> handleConsumer);
}
