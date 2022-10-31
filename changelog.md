# Changelog

## v0.2.0

Features:
  * Hippodamus now supports specifying resource constraints via `ExecutionCoordinator.configure().requiredResource()`.
  * Enhanced logging: Instead of plain and human-only readable log messages, the `Logger` interface now provides a method that allows a type-safe retrieval of the state a task is currently in. 
  * Hippodamus now provides a way to get informed about the creation of a `Handle` before it is used, e.g., in log messages by registering a consumer via `ExecutionCoordinator.configure().onHandleCreation()`.

API changes:
  * The package structure has been changed: All API classes are now in the package `dd.kms.hippodamus.api`.
  * `ExecutionCoordinatorBuilder` does not have a generic parameter anymore.
  * `ExecutionConfigurationBuilder` does not have a generic parameter anymore.
  * Task types are no `int`s anymore, but instances of `TaskType`.
  * The predefined task types have been renamed from "REGULAR" to "COMPUTATIONAL" and from "IO" to "BLOCKING" (motivated by the post [Be Aware of ForkJoinPool#commonPool()](https://dzone.com/articles/be-aware-of-forkjoinpoolcommonpool])
  * The method `submit()` has been removed from the interface `Handle` because it is for internal use only.
  * The method `join()` has been removed from the interface `Handle` because the idea behind Hippodamus discourages explicit joins. Instead, users should specify the handle a task should join on as the task's dependency.
  * The methods `stop()` and `hasStopped()` have been removed from the interface `Handle` because in the context of Hippodamus it does not make sense to stop some but not all tasks. Use the method `ExecutionCoordinator.stop()` to stop all tasks.
  * The interfaces `StoppableExceptionalRunnable` and `StoppableExceptionalCallable` have been removed. A task now simply has to check `Thread.isInterrupted()` or `Thread.interrupted()` to determine whether it should stop instead of checking the Boolean supplier `stopFlag`.
  * The enum `WaitMode` has been removed. You cannot specify anymore how long the `ExecutionCoordinator` waits when all tasks have terminated or been stopped. Now, the coordinator's `close()` method will not return as long as any of its tasks is being executed. This behavior has formerly been described by `WaitMode.UNTIL_TERMINATION`.
  * The class `TaskStoppedException` has been removed. It had been thrown when trying to retrieve the value of a task via `ResultHandle.get()` when the task had already been stopped. Now, a `CancellationException` is thrown instead in this case.
  * The interface `Logger` has been reworked completely. The minimum log level has been abandoned. Instead, `Logger` now has multiple methods for each type of log message.

Behavioral changes:
  * When trying to retrieve the value of task that has thrown an exception, then a `CompletionException` is thrown that wraps that exception.
  * When trying to retrieve the value of a task that has been stopped before it could start executing, then a `CancellationException` is thrown.
  * A task that is in execution and is stopped is not considered terminated anymore. Instead, the task is informed about the stop request via the thread's interrupted flag. It is the task's responsibility whether to react to it or not. This task will not be considered terminated unless it really terminates, either successfully or exceptionally. 
  
## v0.1.0

First Hippodamus release