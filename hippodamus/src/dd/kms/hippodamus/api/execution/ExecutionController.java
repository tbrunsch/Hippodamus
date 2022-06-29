package dd.kms.hippodamus.api.execution;

import dd.kms.hippodamus.api.execution.configuration.ExecutionConfigurationBuilder;

/**
 * A user-defined {@code ExecutionController} can be added to every task via
 * {@link ExecutionConfigurationBuilder#executionController(ExecutionController)} in order to postpone the execution of
 * a task in certain situations. A typical situation is that a task requires much memory and there is currently a high
 * memory consumption. These situations cannot be describes via task dependencies and can usually not be controlled fine
 * enough by specifying the maximum parallelism.<br>
 * <br>
 * Since it is highly user-specific how these situations should be handled, there is no default implementation for this
 * interface you can use. However, there are some sample implementations in some unit tests that are tailored to these
 * tests. These can give an idea of how to implement this interface.
 */
public interface ExecutionController
{
}
