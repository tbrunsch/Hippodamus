# Technical Notes

## Underscore Notation / Reentrant Locking

There are several nested method calls that require a lock on the underlying `ExecutionCoordinator`. Reentrant locking, e.g. via `synchronized`, would lead to a significant overhead. We have tested several alternatives that optimize nested reentrant locking with some success. However, for large number of calls of such methods JVM seems to optimize nested `synchronized` method calls much stronger than it does with our optimizations, making the optimizations counterproductive in these cases. This is why we decided to neither rely on `synchronized` nor on custom optimizations, but on a convention: Instead of synchronizing a nested method, we mark it by using the prefix "_" (underscore) in the method name. Callers of these methods must

1. either be `synchronized` or call such methods within a `synchronized` block or method that use the underlying `ExecutionCoordinator` as lock
1. or by methods with an underscore prefix.

The same rule applies for accessors of fields whose names are prepended by an underscore.  

## How Tasks Are Executed

1. A runnable task is submitted to the `ExecutionCoordinator` via `ExecutionCoordinator.execute()`. The task is wrapped into a callable task that returns `null` and sent to the other overload of `ExecutionCoordinator.execute()`.
1. `ExecutionCoordinatorImpl.execute()`:
    1. Possibly throw an exception that has been occurred until now (either within previous tasks or internal exceptions).
    1. Determine whether the tasks should not be started at all. This is the case if any of the task's dependencies has been stopped.
    1. Create a `ResultHandle` for the task.
    1. If the coordinator has not been stopped and all of its dependencies have completed, then the handle is scheduled for sumbission. Otherwise, the handle will be scheduled when the last of its dependencies completes.
    1. Task scheduling: If task submission is permitted, then the task is submitted via `HandleImpl.submit()`. Otherwise, it is added to the list of pending handles which will be submitted once task permission is permitted. 
1. `HandleImpl.submit()` does the following things:
    1. If the coordinator has not been stopped, then the handle state is set to "SUBMITTED" and the task is submitted to the `ExecutorServiceWrapper`.
1. `ExecutorServiceWrapper.submit()`:
    1. If the task can be submitted (taking the maximum parallelism into account), then it is submitted to the `ExecutorService` via `ExecutorServiceWrapper.submitNow()`. The resulting `Future` is then propagated to the `HandleImpl`, which uses it to stop the task on demand. 
    1. If the task cannot be submitted, then it is added to a collection of unsubmitted tasks and will be submitted later if the load on the `ExecutorService` permits it
    
## Deadlock Prevention When Interacting With ExecutionControllers   

`ExecutionController`s are implemented by the user and they will most likely have their own synchronization mechanism. We must avoid deadlocks that may occur when this mechanism interlocks with Hippodamus' synchronization mechanism. Such interlocking could happen in the following scenario:

* `HandleImpl.executeCallable()` locks the coordinator and then calls `ExecutionController#permitExecution()` via `_startExecution()`. Usually, this call will acquire some kind of lock.
* When a task terminates, then the `ExecutionController` gets informed and might trigger the submission of a task that has been put on hold until now. When this happens, the `ExecutionController` will most likely hold its synchronization lock. The task submission happens by executing the method reference `HandleImpl::submitAsynchronously`, which calls `HandleImpl.submit()` asynchronously. If it would call `HandleImpl.submit()` directly, then we would have the inverse locking order as in `HandleImpl.executeCallable()` because `HandleImpl.submit()` locks the coordinator. This would be a potential deadlock.
    
## ExecutorServiceWrapper and Maximum Parallelism

The maximum parallelism that is specified for each task type is internally considered by the `ExecutorServiceWrapper`. When a task is submitted to the `ExecutorServiceWrapper` and there have are already been `maxParallelism` many tasks submitted to the underlying `ExecutorService` that have not terminated yet, then the `ExecutorServiceWrapper` will queue the new task. We use a priority queue rather than a FIFO queue in which tasks that have been submitted earlier have a higher priority. In the remainer of this section we discuss why.

If we would use a FIFO queue, then we may run into a deadlock if not all task dependencies are specified correctly. For this to see, consider the following example:

```
    ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
        .maximumParallelism(TaskType.COMPUTATIONAL, 2)
        .logger(((logLevel, taskName, message) -> System.out.println(taskName + ": " + message)));
    try (ExecutionCoordinator coordinator = builder.build()) {
        ResultHandle<Integer> task1 = coordinator.execute(() -> returnWithDelay(1));
        ResultHandle<Integer> task2 = coordinator.configure().dependencies(task1).execute(() -> returnWithDelay(task1.get() + 1));
        ResultHandle<Integer> task3 = coordinator.execute(() -> returnWithDelay(task2.get() + 1));
        ResultHandle<Integer> task4 = coordinator.execute(() -> returnWithDelay(task3.get() + 1));
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
```

The implementation of `returnWithDelay()` is:

```
int returnWithDelay(int value) throws InterruptedException {
    Thread.sleep(500);
    return value;
}
```

In this example, the maximum parallelism is 2. Task 4 depends on task 3, which depends on task 2, which depends on task 1. However, the dependencies of task 3 and task 4 are not specified. The program flow will be as follows:

1. The coordinator submits task 1 because it has no dependencies.
1. The coordinator **does not submit** task 2 because it specifies to depend on task 1 and task 1 has not yet finished.
1. The coordinator submits task 3 because it **does not specify** any dependency.
1. The coordinator tries to submit task 4 because it **does not specify** any dependency. Since the `ExecutorService` is already processing 2 tasks (task 1 and task 3), task 4 is queued for later submission.
1. Task 1 terminates, which causes task 2 to be queue for later submission.
1. We can now submit a further task. If we use the FIFO rule, then the queued task 4 is selected for submission because it was queued before task 2. 
1. Task 3 keeps waiting for task 2 to terminate, while task 4 keeps waiting for task 3 to terminate. However, task 2 will never be submitted because of the maximum parallelism.

Note that in this example the coordinator could have avoided the deadlock by submitting task 2 instead of task 4 after task 1 has terminated. This is what a priority queue would have done.

Let us now discuss why we do not run into such problems with a priority queue (aka a *min heap*) with the task IDs as priorities, i.e., where task i has priority i, independent of how wrong the task dependencies are specified (missing dependencies, additional incorrect dependencies) provided
 
 * the underlying `ExecutorService`'s parallelism is larger than or equal to the specified maximum parallelism
 * and there is no state outside of the coordinator that leads to add additional dependencies between the tasks, like handles to other tasks that can be accessed by arbitrary tasks or any kind of synchronization mechanism between the tasks.
 
 For this to see, we first observe that

1. a task i can only depend on tasks j < i (by accessing `ResultHandle.get()`) and
1. a task i can only be declared to depend on tasks k < i (see Section [Task Dependencies](#task-dependencies)).

The first claim stems from the assumption that no additional dependencies between tasks are imposed on the tasks from outside the coordinator.

The second claim is true because when executing a task by calling `ExecutionCoordinator.execute()`, the specified dependencies and the task's code (if we do not apply some hacks) can only refer to handles that have already been created. These handles (and their tasks) have a lower ID than the task at hand.

Now let us assume that our maximum parallelism is M (and the number of available threads is at least M) and that we are in a deadlock with the tasks i_1 < ... < i_M being submitted to the `ExecutorService`. Due to the first claim, task i_1 must depend on a task j < i_1 (real dependency) that has not yet been executed. We will now argue that it is impossible to encounter this situation with a priority queue.

For this, consider all direct and indirect specified (!) dependencies of task j that have not yet been executed plus task j and consider among these tasks the one with the smallest ID k. By construction, all specified dependencies of task k (which must have IDs lower than k) must already have been executed, which makes task k eligible for submission.

Now consider the point in time when the last task completes, i.e., before reaching the deadlock. At that time, only M-1 of the tasks i_1, ..., i_M have already been submitted to the `ExecutorService`. The deadlock is created by submitting the remaining one of these tasks. However, this cannot happen with a priority queue because k <= j < i_1: There was at least one other task (task k) at that time (or earlier) that would have been selected instead. This contradicts the assumption that we ran into a deadlock with the tasks i_1, ..., i_M.

We have shown that priority queues, in contrast to FIFO queues, prevent certain deadlocks if not all dependencies are specified correctly. However, we still can encounter situations in which all but one submitted task are waiting for other tasks to complete. This is why we encourage users to specify all dependencies of their tasks. After all, one main objective of Hippodamus is to exploit dependency information for improving the performance.