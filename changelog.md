# Changelog

## v0.2.0

API changes:
  * The package structure has been changed: All API classes are now in the package `dd.kms.hippodamus.api`.
  * `ExecutionCoordinatorBuilder` does not have a generic parameter anymore.
  * `ExecutionConfigurationBuilder` does not have a generic parameter anymore.
  * Task types are no `int`s anymore, but instances of `TaskType`.
  * The predefined task types have been renamed from "REGULAR" to "COMPUTATIONAL" and from "IO" to "BLOCKING" (motivated by the post [Be Aware of ForkJoinPool#commonPool()](https://dzone.com/articles/be-aware-of-forkjoinpoolcommonpool])
  * The method `submit()` has been removed from the interface `Handle` because it is for internal use only.
  * The interfaces `StoppableExceptionalRunnable` and `StoppableExceptionalCallable` have been removed. A task now simply has to check `Thread.isInterrupted()` or `Thread.interrupted()` to determine whether it should stop instead of checking the Boolean supplier `stopFlag`.
  * The enum `WaitMode` has been removed. You cannot specify anymore how long the `ExecutionCoordinator` waits when all tasks have terminated or been stopped. Now, the coordinator's `close()` method will not return as long as any of its tasks is being executed. This behavior has formerly been described by `WaitMode.UNTIL_TERMINATION`.

## v0.1.0

First Hippodamus release