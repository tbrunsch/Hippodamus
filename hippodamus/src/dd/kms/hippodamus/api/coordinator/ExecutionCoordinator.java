package dd.kms.hippodamus.api.coordinator;

import dd.kms.hippodamus.api.exceptions.ExceptionalRunnable;
import dd.kms.hippodamus.api.execution.ExecutionManager;
import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;

import java.util.concurrent.ExecutorService;

/**
 * This is the central class of Hippodamus. It handles the execution of tasks. The coordinator
 * must be used with try-with-resources:
 * <pre>
 * try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
 *     coordinator.execute(() -> System.out.println("Hello World!"));
 *     ResultHandle<Double> value = coordinator.execute(Math::random);
 * }
 * </pre>
 * See the documentation for details.
 */
public interface ExecutionCoordinator extends ExecutionManager, AutoCloseable
{
	/**
	 * Call this method to configure how a certain task has to be executed.
	 */
	ExecutionConfigurationBuilder configure();

	/**
	 * Call this method to permit/deny submission of tasks to an {@link ExecutorService}.<br>
	 * By default, task submission is permitted. In that case, tasks can already be executed although not
	 * all tasks have been registered at the coordinator via {@link #execute(ExceptionalRunnable)}
	 * or related methods. However, the coordinator can only manage exceptions that are thrown inside the tasks.
	 * It cannot handle exceptions that are directly raised inside the try-block. If such an exception occurs,
	 * all executed tasks will run to end while the coordinator is closing (because the coordinator does not get
	 * informed about this exception). Only then, the exception will be caught by any exception handler. Hence,
	 * a lot of time may be wasted between throwing and catching the exception.<br>
	 * You can avoid wasting that time by guarding the code inside the try-block with calls to this method:<br>
	 * <pre>
	 * try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
	 *     coordinator.permitTaskSubmission(false);
	 *     // register your tasks here: coordinator.execute(...)
	 *     coordinator.permitTaskSubmission(true);
	 * }
	 * </pre>
	 * <b>Note:</b> When calling {@code permitTaskSubmission(true)}, this does not only permit the submission of
	 * newly registered tasks. Tasks, that are eligible for submission, but have not been submitted yet, will
	 * immediately be submitted.
	 */
	void permitTaskSubmission(boolean permit);

	/**
	 * Stops all tasks created by this service and all of their dependencies.
	 */
	void stop();

	/**
	 * Checks if any of the current tasks has already thrown an exception. If so, it throws that exception,
	 * provided it has not already thrown them in a previous call to {@code checkException()}.<br>
	 * <br>
	 * Calling this method is usually <b>not necessary</b> because it is automatically calling when executing
	 * further tasks and at the end of the try-block. However, if you decide, for whatever reason, that you
	 * need a time-consuming block in the {@link ExecutionCoordinator}'s thread within the coordinator's try-block,
	 * then it makes sense to call that method on a regular basis to react to exceptions faster.
	 */
	void checkException();

	@Override
	void close();
}
