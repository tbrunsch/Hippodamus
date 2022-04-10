# Technical Notes

## How Tasks Are Executed

1. A runnable task is submitted to the `ExecutionCoordinator` via `ExecutionCoordinator.execute()`. The task is wrapped into a callable task that returns `null` and sent to the other overload of `ExecutionCoordinator.execute()`.
1. `ExecutionCoordinatorImpl.execute()`:
    1. Possibly throw an exception that has been occurred until now (either within previous tasks or internal exceptions).
    1. Determine whether the tasks should not be started at all. This is the case if any of the task's dependencies has been stopped.
    1. Create a `ResultHandle` for the task.
    1. If the task has not been stopped and all of its dependencies have completed, then the handle is scheduled for sumbission. Otherwise, the handle will be scheduled when the last of its dependencies completes.
    1. Task scheduling: If task submission is permitted, then the task is submitted via `ResultHandleImpl.submit()`. Otherwise, it is added to the list of pending handles which will be submitted once task permission is permitted. 
1. `ResultHandleImpl.submit()` does the following things:
    1. If the task has not been stopped, then the handle state is set to "SUBMITTED" and the task is submitted to the `ExecutorServiceWrapper`.
1. `ExecutorServiceWrapper.submit()`:
    1. If the task can be submitted (taking the maximum parallelism into account), then it is submitted to the `ExecutorService` via `ExecutorServiceWrapper.submitNow()`. The resulting `Future` is then propagated to the `ResultHandleImpl`, which uses it to stop the task on demand. 
    1. If the task cannot be submitted, then it is added to a collection of unsubmitted tasks and will be submitted later if the load on the `ExecutorService` permits it.     