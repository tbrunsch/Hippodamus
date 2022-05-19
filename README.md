# Hippodamus

Hippodamus is a Java library for writing parallel code in an elegant way. It provides some features of sequential code that parallel code usually lacks:

---
Hippodamus = [Nursery](https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/) + Dependency Management + Exception Handling
___

The API design was motivated by several practical applications that were ugly to implement with [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html).  

## Nursery

In his post "[Notes on structured concurrency, or: Go statement considered harmful](https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/)", Nathaniel J. Smith discusses problems that arise when spawning threads. The main challenge is that the control flow splits and that the developer is responsible for joining the different flow branches later. The author likens this to the problem with the `goto` statement that tempts users to write spaghetti code. As a remedy, Smith proposes a pattern for writing parallel code, a so-called *nursery*. Nurseries are code blocks which cannot be left unless all threads spawned in that block have terminated. Although this concept has been discussed controversially and is maybe a bit overhyped by the author, it has been taken as the basis for this parallelization framework.

At a first glance it seems that nurseries only save writing some `join()` calls. However, it turns out that a central position in the code where all threads join allows an advanced exception handling mechanism comparable to that for sequential code.    

## Dependency Management

Managing the dependencies of parallel tasks, i.e., which tasks have to be executed before another task, was one main objective when designing the Hippodamus API. Java's `CompletableFuture` provides multiple ways to specify such dependencies: `allOf()`, `thenAccept()`, and `runAfterBoth()`, just to mention some of them. Typically, the fluent style is used to specify that one task has to be executed after another. 

Hippodamus uses a sequential style. Each task is assigned a `Handle` (comparable to a `Future`). Whenever you execute a task (we use the term *execute* instead of *submit* since the distinction between whether a task is immediately executed or only submitted to a queue is considered a technical detail and of minor importance to the API user), you may specify the handles of all tasks the task depends on.

Note that there is **no direct support** for executing a task if any of a set of tasks completes as provided by the `CompletableFuture` API (`anyOf()`, `runAfterEither()`). However, there is a concept of aggregation and short circuit evaluation that covers important use cases which would be modelled with such methods (see Section [Aggregation](#aggregation) for more details).

## Exception Handling

Hippodamus provides a comfortable way of handling exceptions similar to exception handling in sequential code. `CompletableFuture` does not support checked exceptions. Whenever your tasks may throw such exceptions, you have to wrap them in an unchecked `CompletionException`. To handle such exceptions, you have to install an exception handler for the `Future` that is associated with that task.

In Hippodamus, you write a try-with-resources block, in which you may execute tasks in parallel. These tasks are allowed to throw checked exceptions. Since Hippodamus guarantees that the try-block is not left unless all tasks have terminated, there is a predestined place where to handle exceptions thrown in any of these tasks: a catch-block. If a task only declares to throw one type of exception, then Hippodamus can even infer the exact exception type that has to be caught.

Since Hippodamus assumes that everything within a try-block forms one super task that has been split into sub task, it will stop all running tasks if at least one task terminates exceptionally.

When working with Hippodamus, it is advantageous to understand how exception handling is realized by the framework. For further details see Section [Exception Handling Magic](#exception-handling-magic).

## When To Use Hippodamus

Hippodamus **does not** cover all use cases that can be tackled with the `CompletableFuture` API. Instead, it is optimized for the following use case:

- There is a super task that can be split into sub tasks.
- The sub tasks can (partially) be executed in parallel.
- An exceptional termination of one tasks should result in an exceptional termination of the super task.
- There are no cyclic dependencies between the sub tasks.

You will gain even more benefit by using Hippodamus if at least one of the following applies:

- You have sequential code that you want to parallelize.
- The sub tasks depend on each other, i.e., some tasks must not be executed before some others have completed. The more complex these dependencies, the better.
- Some sub tasks throw checked exceptions.
- You want a comfortable exception handling mechanism.

# Coordinators

A coordinator manages parallel tasks. It is a resource and should therefore be used in a try-with-resources statement.

**BasicSample.java:**

```
try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
    coordinator.execute(() -> System.out.println("Hello "));
    coordinator.execute(() -> System.out.println("World!"));
}
```

As can be seen in the example, the entry point to the framework is the class `dd.kms.hippodamus.api.coordinator.Coordinators`. This is a factory class for two types of coordinators:

1. `ExecutionCoordinator`s and
1. `AggregationCoordinator`s.

An `AggregationCoordinator` is a special case of an `ExecutionCoordinator` that offers additional support for super tasks that aggregate data of their sub tasks. For more details see Section [Aggregation](#aggregation). 

For each of these coordinator types, the class `Coordinators` offers two factory methods: One method creates a preconfigured coordinator, while the other one returns a builder via which the coordinator can be configured (see Section [Configuring Coordinators](#configuring-coordinators)).

## Configuring Coordinators

To configure a coordinator before using it, you must call either of the two methods:

1. `Coordinators.configureExecutionCoordinator()` or
1. `Coordinators.configureAggregationCoordinator()`.

This returns a builder that allows you to configure:

- Which `ExecutorService` to use for which *task type* and whether to shutdown the service when the coordinator is closed. See Section [Task Types](#task-types) for more details about task types.
- The maximum number of tasks of a certain type that may be processed in parallel (see Section [Controlling Parallelism](#controlling-parallelism))
- The minimum log level and which logger to use.
- Whether to verify task dependencies (see sections [Task Dependencies](#task-dependencies) and [Dependency Verification](#dependency-verification)).
- When the coordinator should terminate (see Section [Stopping Tasks](#stopping-tasks))

**LoggingSample.java:**

```
ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
    .logger((logLevel, taskName, message) -> System.out.println(taskName + ": " + message))
    .minimumLogLevel(LogLevel.STATE);
try (ExecutionCoordinator coordinator = builder.build()) {
    coordinator.configure().name("'Hello' task").execute(() -> System.out.println("Hello "));
    coordinator.configure().name("'World' task").execute(() -> System.out.println("World!"));
}
```

Note that in the previous example also the tasks are configured (see Section [Configuring Tasks](#configuring-tasks)) for a more concrete debug output. Additionally, the output is not guaranteed to be "Hello World!" because both tasks are executed in parallel independent of each other. See Section [Task Dependencies](#task-dependencies) for how to ensure that tasks are executed in a certain order.

## Executing Tasks

There are two ways to execute a task within a coordinator's try-block:

1. By directly calling `ExecutionCoordinator.execute()` or
1. By calling `ExecutionCoordinator.configure()`.

The first method is used for executing tasks that need no further configuration. This is the case for computational tasks (see Section [Task Types](#task-types)) without dependencies. In all other cases you should call the second method.

When executing a task, you get a `Handle` (comparable to a `Future`) to that task that allows some control over the task. In particular, it allows specifying dependencies between tasks.  

## Configuring Tasks

To configure tasks before executing them, you must call `ExecutionCoordinator.configure()`. This returns a builder that allows to configure:

- The name of the task. The name will be used for logging and can be useful for debugging.
- The type of the task. For more information see Section [Task Types](#task-types).
- The handles of the tasks the task depends on. See Section [Task Dependencies](#task-dependencies) for more details. 

## Task Types

`CompletableFuture`s allow specifying `ExecutorService`s for each task. This is reasonable because, e.g., blocking tasks should not jam the common `ExecutorService`.

Hippodamus abstracts from this concept by separating

1. What kind a task is (task type) and
1. How a certain task type should be handled.

There are two predefined types of tasks defined in `dd.kms.hippodamus.api.coordinator.TaskType`: computational tasks (`TaskType.COMPUTATIONAL`) and blocking tasks (`TaskType.BLOCKING`). You can define further `TaskType`s by calling `TaskType.create(id)` for any non-negative id. By default, a task is assumed to be a computational task.

When configuring the coordinator (see Section [Configuring Coordinators](#configuring-coordinators)), you can specify which `ExecutorService` to use for which task type. When configuring a task, you can specify which type it is. Hence, you indirectly specify which `ExecutorService` it will be submitted to.

Since the `ExecutorService`s are not bound to the tasks, but to the coordinator, shutting them down (if desired) can be automatically done in the coordinator's `close()` method.

Note that, by default, computational tasks are sent to the common `ForkJoinPool`, whereas blockung tasks are sent to a dedicated single-threaded `ExecutorService`.

**TaskTypeSample.java:**

```
try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
    for (int i = 1; i <= 10; i++) {
        int count = i;
        coordinator.configure().taskType(TaskType.COMPUTATIONAL).execute(() -> printWithDelay("Finished computational task " + count));
        coordinator.configure().taskType(TaskType.BLOCKING)     .execute(() -> printWithDelay("Finished blocking task " + count));
    }
} catch (InterruptedException e) {
    e.printStackTrace();
}
```

The implementation of `printWithDelay()` is:

```
void printWithDelay(String s) throws InterruptedException {
    Thread.sleep(500);
    System.out.println(s);
}
```

The previous example creates 10 computational tasks and 10 blocking tasks. When executing the code, you can see that the computational tasks are executed in parallel, while the blocking tasks are executed sequentially. Note that:

- It is not necessary to specify the type of computational tasks. In the example we only did this to emphasize the different types.
- As already mentioned in Section [Exception Handling](#exception-handling), it is no problem if tasks throw checked exceptions. The framework forces you to handle these exceptions. For more details see Section [Exception Handling Magic](#exception-handling-magic).
- In many of the samples in this documentation we have to catch an `InterruptedException`. This is only due to the fact that we use the method `Thread.sleep()` to simulate the execution of a real task that requires a certain amount of time.   

## Getting Task Values

Some tasks return values. To access their values, you do not simply reference this task via a `Handle`, but by `ResultHandle`. There are different situations in which the value of a task X may be retrieved:

1. Another task Y needs the value of X:

    We strongly encourage users to specify X as dependency of Y (see Section [Task Dependencies](#task-dependencies)) in this case. This ensures that task Y is not executed before X terminates regularly and, hence, guarantees that task Y does not block a thread and that no exception is thrown when retrieving the value of X.
     
1. A completion listener needs the value of X.

    Completion listeners of task X will be called if and only if task X terminates regularly. This is why completion listeners can access the result without having to wait and no exception will be thrown when retrieving the value of X. 
    
1. The user (the creator of the coordinator) needs the value of X.

    The most common example for this is when the user wants to know the final outcome of his computation after the coordinator has terminated. It is also possible to retrieve the value of X while the coordinator is still running, but we currently do not see a reason why this should be necessary.

Task X can be in different states when someone tries to receive its value:

1. It might not yet have been executed.

    In this case, the retrieval of the value of X will block until one of the states 3, 4, or 5 is reached.

1. It might be in execution.

    In this case, the retrieval of the value of X will block until one of the states 4 or 5 is reached.

1. It might have been stopped before it has terminated.

    In this case, a `CancellationException` is thrown. There are several reasons why we decided not to throw an `InterruptedException` instead:
    
    * If Hippodamus is used as intended, then this scenario should not occur (see also table below): If the task has been stopped because the user has stopped the whole coordinator, then it is the user's responsibility not to access task values afterwards. If the task has been stopped because of an exception, then depending tasks should not be executed at all. Additionally, this exception will be thrown in the coordinator's thread. In this case, the user should not try to access any task's value at all.
    
    * An `InterruptedException` is a checked exception. We did not want to force users to write a handler for an exception that will not be thrown if they use Hippodamus as intended.
    
    * Hippodamus was designed as a parallelization framework that allows writing code that is close to sequential code. Having to handle `InterruptedException`s forces users to think about technical details of parallelization. 

1. It might have completed/terminated regularly.

    In this case, the value of X is returned immediately without blocking.

1. It might have terminated exceptionally. 

    In this case, the exception that has been thrown within task X is wrapped within a `CompletionException`, which is thrown when retrieving the value of X. When using Hippodamus as intended, then this case should not occur (see table below).

The following table shows which combinations of who tries to retrieve the value of a task and in which state task is are possible:

| |not yet executed|executing|stopped before termination|terminated regularly|terminated exceptionally|
|---|:---:|:---:|:---:|:---:|:---:|
|**another task**|(x)|(x)|(x)|**x**|(x)|
|**completion listener**|-|-|-|**x**|-|
|**user**|?|?|!|**x**|!|

The following legend explains the symbols:

|Symbol|Explanation|
|:---:|---|
|x|This combination is possible and intended.|
|-|This combination is not possible.|
|(x)|This combination is not possible when the task is specified as dependency of the other task.|
|?|This combination occurs when the user tries to access a task's value within the coordinator's try block. We support it although we do not see its necessity.| 
|!|This combination is not intended and supported suboptimally: A `CancellationException` or a `CompletionException` is thrown, respectively, although it would be better to throw an `InterruptedException` or the real exception instead, respectively. However, this would complicate using Hippodamus the intended way. 

## Handles vs. CompletableFutures

A `ResultHandle` is similar to a `CompletableFuture`, but with much less functionality. The reason is that most of the functionality of `CompletableFuture` is not required when using Hippodamus.

Unlike `CompletableFuture`, Hippodamus has a class `Handle` for representing tasks that do not return a value. This slightly simplifies parallelizing such tasks because we do not have to wrap them within a task that returns `Void`.

The class `Handle` does not provide a `join()` method because there is no need to join on a handle: When the coordinator terminates, then it guarantees that no task is running anymore (nursery). When a task requires that another task has completed before it can run, then you have to specify that the former depends on the latter. This also avoids that a task blocks a thread by waiting for another task. See Section [Task Dependencies](#task-dependencies) for details.

`CompletableFuture` has two similar methods: `join()` and `get()`. There are only two differences between both:

1. `join()` throws an unchecked `CompletionException` whereas `get()` throws a checked `ExecutionException` if the task has terminated exceptionally and
1. `get()` is interruptible (i.e. it could throw an `InterruptedException`) while `join()` is not.

Unlike the name suggests, `ResultHandle.get()` behaves like `CompletableFuture.join()` and not `CompletableFuture.get()`. This makes `ResultHandle.get()` more convenient when used the intended way. See Section [Getting Task Values](#getting-task-values) for more details about this decision.

## Task Dependencies

Specifying dependencies prevents a task from being executed before tasks it depends on have completed. There are two situations in which this is important:

1. Correctness: In some cases it would be wrong to execute a task earlier because the environment it is executed in is in a wrong state at that point in time.
1. Performance: If tasks are running and actively waiting for results of their dependencies, then they are blocking a thread of the `ExecutorService` that could otherwise be used for other tasks that are ready to run. 

To specify the tasks a task depends on, one has to configure the task and list the handles of those tasks. 

**DependencySample.java:**

```
try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
    ResultHandle<Integer> value1 = coordinator.execute(() -> returnWithDelay(5));
    ResultHandle<Integer> value2 = coordinator.execute(() -> returnWithDelay(7));

    // without specifying dependencies
    coordinator.execute(() -> System.out.println(value1.get() + value2.get()));

    // with specifying dependencies
    coordinator.configure().dependencies(value1, value2).execute(() -> System.out.println(value1.get() + value2.get()));
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

Note that both summation tasks will print 12 to the console. However, the first summation task is immediately submitted to an `ExecutorService`, waiting for the addend tasks to complete, whereas the second summation task will not be submitted to the `ExecutorService` unless both addend tasks have completed.

### Dependency Verification

Specifying dependencies can help reducing the number of tasks that actively wait for other tasks to complete and prevent other tasks that are ready to run from being executed. Hippodamus provides a mechanism to check that required dependencies are specified. The mechanism simply throws a `CoordinatorException` if someone tries to access the value of a task that has not yet completed. Since such an access is not a problem per se, the mechanism is deactivated by default. We suggest to activate it only during development.

To activate the dependency verification mechanism, you have to configure the `ExecutionCoordinator` accordingly.

**DependencyVerificationSample.java:**

```
ExecutionCoordinatorBuilder builder = Coordinators.configureExecutionCoordinator()
    .verifyDependencies(true);
try (ExecutionCoordinator coordinator = builder.build()) {
    ResultHandle<Integer> value = coordinator.execute(() -> returnWithDelay(7));

    // we do not specify first task as dependency => CoordinatorException because dependencies are verified
    coordinator.execute(() -> System.out.println(value.get()));
} catch (InterruptedException e) {
    e.printStackTrace();
} catch (CoordinatorException e){
    System.out.println("Oops. Forgot to specify that the second task depends on the first one...");
}
```

The implementation of `returnWithDelay()` is:

```
int returnWithDelay(int value) throws InterruptedException {
    Thread.sleep(500);
    return value;
}
```

## Controlling Parallelism

Sometimes it is useful to specify a limit for the number of tasks of a certain type that may be executed in parallel. A typical use case is when the tasks consume scarce resources or have heavy resource consumptions.

One possibility is to specify a dedicated `ExecutorService` for theses tasks (see Section [Configuring Coordinators](#configuring-coordinators)) whose parallelism is limited to the desired number. However, specifying dedicated `ExecutorService`s does not scale as well as using a shared `ExecutorService`.

Alternatively, you can specify the maximum parallelism for a certain task type. This is the maximum number of tasks of that type processed by their `ExecutorService` at any time. Surplus tasks will be queued until one of the tasks currently be processed by the `ExecutorService` terminates.

For the sake of completeness we discuss in Section [Maximum Parallelism And Deadlocks](#maximum-parallelism-and-deadlocks) why we internally use a priority queue instead of a simple FIFO queue. These are just technical details and not relevant for users of Hippodamus. 

### Maximum Parallelism And Deadlocks

In this section we discuss why we use a priority queue rather than a FIFO queue for queuing surplus tasks that cannot immediately be submitted to the `ExecutorService` due to the specified maximum parallelism. The reason is that one may run into a deadlock when using a FIFO queue if not all task dependencies are specified correctly. For this to see, consider the following example:

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

Let us now discuss why we do not run into such problems with a priority queue (aka a *min heap*) with the task IDs as priorities, i.e., where task i has priority i, independent of how wrong the task dependencies are specified (missing dependencies, additional incorrect dependencies). For this to see, we first observe that

1. a task i can only depend on tasks j < i (by accessing `ResultHandle.get()`) and
1. a task i can only be declared to depend on tasks k < i (see Section [Task Dependencies](#task-dependencies)).

This is due to the fact that when executing a task by calling `ExecutionCoordinator.execute()`, the specified dependencies and the task's code (if we do not apply some hacks) can only refer to handles that have already been created. These handles (and their tasks) have a lower ID than the task at hand.

Now let us assume that our maximum parallelism is M and that we are in a deadlock with the tasks i_1 < ... < i_M being submitted to the `ExecutorService`. In particular, task i_1 must depend on a task j < i_1 (real dependency) that has not yet been executed. We will now argue that it is impossible to encounter this situation with a priority queue.

For this, consider all direct and indirect specified (!) dependencies of task j that have not yet been executed plus task j and consider among these tasks the one with the smallest ID k. By construction, all specified dependencies of task k (which must have IDs lower than k) must already have been executed, which makes task k eligible for submission.

Now consider the point in time when the last task completes, i.e., before reaching the deadlock. At that time, only M-1 of the tasks i_1, ..., i_M have already been submitted to the `ExecutorService`. The deadlock is created by submitting the remaining one of these tasks. However, this cannot happen with a priority queue because k ≤ j < i_1: There was at least one other task (task k) at that time (or earlier) that would have been selected instead. This contradicts the assumption that we ran into a deadlock with the tasks i_1, ..., i_M.

We have shown that priority queues, in contrast to FIFO queues, prevent certain deadlocks if not all dependencies are specified correctly. However, we still can encounter situations in which all but one submitted task are waiting for other tasks to complete. This is why we encourage users to specify all dependencies of their tasks. After all, one main objective of Hippodamus is to exploit dependency information for improving the performance.  

## Aggregation

Hippodamus particularly supports the use case in which a super task aggregates the values of (some of its) sub tasks. You will probably encounter such use cases more often than you might think.

**Example:** One of the fundamental assumptions imposed by Hippodamus is that the super task fails if at least one of its sub tasks fails. If failing is signaled by throwing an exception, then this behavior is already achieved by using the `ExecutionCoordinator`. However, if sub tasks signal their success by returning a `boolean` value, then an `AggregationCoordinator` is required. The boolean values are aggregated into one boolean value describing the success of the super task by applying a logical conjunction:     

**BooleanAggregationSample.java:**

```
Aggregator<Boolean, Boolean> successAggregator = Aggregators.conjunction();
try (AggregationCoordinator<Boolean, Boolean> coordinator = Coordinators.createAggregationCoordinator(successAggregator)) {
    for (int i = 0; i < 10; i++) {
        coordinator.aggregate(() -> runTask());
    }
}
System.out.println("Super task successful: " + successAggregator.getAggregatedValue());
```

In the previous example, the method `runTask()` returns a boolean value describing the success of the sub task. The value aggregated until a certain point in time can be queried via the method `Aggregator.getAggregatedValue()`.

### Aggregators

The interface `dd.kms.hippodamus.api.aggregation.Aggregator` describes how values of a type `S` have to be aggregated to a value of type `R`. In many cases `S` and `R` will be identical. You can either directly implement that interface or use the factory class `dd.kms.hippodamus.api.aggregation.Aggregators` to construct an aggregator. This factory provides methods for creating

- Disjunction (logical or) and conjunction (logical and) aggregators and
- Aggregators based on an initial value, an aggregation function, and a predicate that can be used to test whether aggregation can complete prematurely (see Section [Short Circuit Evaluation](#short-circuit-evaluation)).

### Aggregating Sub Task Values

To aggregate the value of a sub task, you simply call the method `AggregationCoordinator.aggregate()` (instead of `ExecutionCoordinator.execute()`). Of course, only tasks are accepted that have a return value that match the expectation of the aggregator that has been specified when constructing the `AggregationCoordinator`.

Note that an `AggregationCoordinator` is just a special `ExecutionCoordinator` and that the `aggregate()` method does the same as the `execute()` method plus some additional aggregation logic. Hence, aggregation tasks are not different from other tasks. They just get some extra treatment. Consequently, it is valid to mix aggregation tasks and other tasks:

```
    ResultHandle<Double> factor1 = coordinator.execute(() -> getFirstFactor());
    ResultHandle<Double> factor2 = coordinator.execute(() -> getSecondFactor());
    coordinator.configure().dependencies(factor1, factor2).aggregate(() -> factor1.get()*factor2.get());
``` 
Of course, only the values of aggregation tasks will be aggregated.

### Short Circuit Evaluation

Java uses short circuit evaluation when evaluating disjunctions and conjunctions: When evaluating a boolean expression from left to right and the outcome is already clear after evaluating a certain subexpression, Java stops evaluating further subexpressions. This is, e.g., the case in the expression `b1 || b2` if `b1` is true. In that case, the value of `b1 || b2` is true, independent of the value of `b2`. Therefore, the value of `b2` is not evaluated at all.

Short circuit evaluation is not only an optimization, but it is sometimes also necessary to write readable code: Without short circuit evaluation, the expression `s != null && s.length() > 0` would result in a `NullPointerException` if `s` is null. Due to short circuit evaluation, the subexpression `s.length() > 0` is not evaluated if `s` is null. 

The `Aggregator` interface provides a method `hasAggregationCompleted()` that is queried to check whether short circuit evaluation can be applied or not. If so, the `AggregationCoordinator` will stop all aggregation tasks and complete prematurely.

## Stopping Tasks

It is not possible to stop individual tasks manually. Since all tasks are part of a super task that is meant to be processed parallely by the coordinator, stopping some tasks but not all does not make sense. You can only stop the whole coordinator (see Section [Stopping Coordinators](#stopping-coordinators)), which internally stops all tasks individually. If a task is stopped and it has not yet been executed, then it will never be executed at all. If a task is executing when it is requested to stop, then the interrupt flag of the thread that executes the task is set. It is the task's responsibility to check the interrupted flag by, e.g., calling  `Thread.isInterrupted()` or `Thread.interrupted()`.

## Stopping Coordinators

Currently there are three ways to stop a coordinator:

- Manually by calling `ExecutionCoordinator.stop()`,
- Automatically by the `ExecutionCoordinator` as a reaction to an exception, and
- Automatically by the `AggregationCoordinator` as a consequence of short circuit evaluation.

# Task Listeners

Hippodamus was designed to enable writing code that looks as much sequential as possible. Hence, one intention was to eliminate the need for registering listeners. However, since we did not want to unnecessarily limit its applicability, Hippodamus provides a basic listener concept for `Handle`s: You can register listeners for the case that a task finishes regularly or exceptionally. For registering a listener, you call on of the methods

- `Handle.onCompletion(Runnable)` or
- `Handle.onException(Runnable)`,

respectively. Note that, unlike in the `CompletableFuture` API, the listeners are pure `Runnable`s instead of consumers of the result value or an exception. This makes it a bit more flexible because the runnable could also be a lambda that captures the handle. We propose the following procedure for registering listeners:

```
    Handle handle = coordinator.execute(() -> doSomething());
    handle.onCompletion(() -> onHandleCompleted(handle));
```

The question remains where and when the listener is executed. This depends on the state of the coordinator at the point of time when the listener is registered:

- If the task has already completed or thrown an exception, respectively, then the listener is executed immediately in the coordinator's thread.
- If the task completes (or throws an exception) later, then the listener is called in the thread that executed the task. 

# Exception Handling Magic

Exception handling in Hippodamus works as follows:

1. The `ExecutionCoordinator` declares to throw those exceptions in `execute(<task>)` that the task declares to throw.  
1. Each `Handle` delegates an encountered exception to the coordinator.
1. At certain points, the coordinator checks whether an exception has occurred. If so, it throws it in the coordinator's thread.

Let us discuss these points in more detail:

1. Since the `execute()` methods of the `ExecutionCoordinator` do not immediately execute the specified task, they do not really throw the task's exceptions. These exceptions will be thrown later. However, since the `execute()` methods are the only place where we know at compile time which exceptions may be thrown, these methods pretend to throw them to force the user to handle them. Without this trick, the user had to handle generic `Throwable`s instead of concrete exception classes.
1. The second point is simple: `Handle`s catch the exceptions of their tasks and inform the coordinator via a setter about these exceptions. The coordinator stores this information in a field and stops all tasks. The coordinator **does not throw** exceptions at that time because they would be thrown in the task's instead of the coordinator's thread.
1. The `ExecutionCoordinator` has a method `checkException()` that checks whether its exception field is set. If so, it throws this exception. Note that this method **might throw checked exceptions** although it does not declare to do so. This is a well-known trick based on type erasure (see `dd.kms.hippodamus.impl.exceptions.Exceptions.throwUnchecked(Throwable)`). With this trick we bypass Java's exception handling mechanism locally. However, by the first point we ensure that the user handles these type of exceptions nevertheless. (After all, we want to provide an exception handling mechanism.)
The method `checkException()` **must only be called in the coordinator's thread** such that the exception is caught by the correct handler. The coordinator calls this method in its `execute()` methods and in its `close()` method.

## When Exceptions Are Thrown

It is important to known when exceptions are thrown. From the third point of Section [Exception Handling Magic](#exception-handling-magic) it follows that this is only the case when executing a task or after all code in the coordinator's try-block has been executed and the coordinator's `close()` method is called. Consider the following example:

**InfiniteLoopSample.java:**

```
try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
    coordinator.execute(() -> { throw new Exception("Break infinite loop!"); });
    while (true);
} catch (Exception e) {
    System.out.println("Reached unreachable code!");
}
```

In this example, the exception will be thrown in the task's thread and delegated to the coordinator. However, the coordinator is never given a chance to throw that exception in its thread: There is no further `execute()` call and the `close()` method is never reached because the try-block cannot be left. Hence, the exception does not break the infinite loop.

Of course, the previous example is constructed artificially. However, you will encounter similar problems if you execute sequential code within the coordinator's try block that runs for quite some time. During that time, the coordinator cannot throw any exceptions. If you really have to write such code, then you should manually call `ExecutionCoordinator.checkException()` on a regular basis.   

Knowing about this behavior does not only help to avoid pitfalls, but it also allows intentionally writing code blocks which cannot be interrupted by task exceptions:

```
    Handle handle = coordinator.execute(() -> doSomething());
    handle.onException(() -> logException(handle));
```

Even in the case that, for whatever reason, the task terminates exceptionally before the exception handler has been registered, the coordinator does not throw any exception before the listener is registered. Hence, these two lines form an atomic block: It is not possible that the coordinator terminates due to the task's exception without the listener being informed.   

## Tasks With Multiple Exceptions

When calling `ExecutionCoordinator.execute()`, Hippodamus utilizes type inference to force the API-user to write an exception handler for the exceptions possibly thrown by the task. This works great if a task only throws one type of exception. If it throws different types of exceptions, Java's type inference mechanism determines a common exception class for these exceptions that the user has to handle. This is a bit unsatisfying, but we are not aware of a way to solve this problem.  

## Internal Errors

Hippodamus throws `dd.kms.hippodamus.api.exceptions.CoordinatorException`s when an internal error occurs. Internal errors are errors in the framework or in the usage of the framework. Errors in the usage of the framework include the following scenarios:

- Dependency verification is enabled and there is an attempt to access the value of a task that has not yet completed (cf. Section [Dependency Verification](#dependency-verification)).
- The logger throws an exception (cf. Section [Configuring Coordinators](#configuring-coordinators)).
- A completion or exception listener throws an exception (cf. Section [Task Listeners](#task-listeners)).

Internal errors are treated with the highest priority as they are fatal. If a task throws an exception and, e.g., an exception listener which is notified also throws an exception, then the coordinator will throw a `CoordinatorException` containing information about the listener that threw an exception. The task's exception is ignored in this case.

## Intractable Exceptions

The `ExecutionCoordinator` manages exceptions that are thrown in tasks as well as exceptions thrown by loggers and task listeners. However, there are exceptions that are out of the coordinator's control. We call such exceptions *intractable*. Intractable exceptions are exceptions that are directly thrown in the coordinator's try-block:

**IntractableExceptionSample.java:**

```
String s = 42 % 7 == 0 ? null : "Never used";
try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
    coordinator.execute(() -> Thread.sleep(2000));
    System.out.println(s.length());	// NPE
} catch (InterruptedException e) {
    e.printStackTrace();
}
```

In the previous example, a `NullPointerException` is thrown and will finally leave the coordinator's try-block. However, the coordinator is unaware of that exception and will wait in its `close()` method for the task to complete. This is an unnecessary delay. As a remedy, the `ExecutionCoordinator` allows you to control when the submission of tasks to an `ExecutorService` is allowed and when not by calling `permitTaskSubmission()`:

```
String s = 42 % 7 == 0 ? null : "Never used";
try (ExecutionCoordinator coordinator = Coordinators.createExecutionCoordinator()) {
    coordinator.permitTaskSubmission(false);
    coordinator.execute(() -> Thread.sleep(2000));
    System.out.println(s.length());	// NPE
    coordinator.permitTaskSubmission(true);
} catch (InterruptedException e) {
    e.printStackTrace();
}
```

In the modified example, no task is executed until the statement `coordinator.permitTaskSubmission(true)`. Since the `NullPointerException` is thrown before, no task is running when `ExecutionCoordinator.close()` is called. Since task submission is not permitted at that point, the `ExecutionCoordinator` can immediately stop and does not have to wait for the pending task. 

### Design Rationale

A similar problem occurs when using try-with-resources to implement a transaction. This problem is often solved by committing the transaction at the end of the try-block:

```
try (Transaction transaction = Transaction.create()) {
    // perform transaction (might throw an exception):
    // ...

    // finally commit the transaction
    transaction.commit();
}
```

In the transaction's `close()` method it is checked whether `commit()` has been called or not. If so, the transaction is committed. Otherwise, it will be rolled back. This approach only uses one additional method call instead of two. Nevertheless, we have some arguments on our side as well:

- We think that the problem is not that we have to write an additional line (or two), but that we might forget to write it. However, forgetting to write the second line after writing the first seems very unlikely.
- In the transaction setting it is essential to write that additional line. In our setting, in most cases it "only" costs some extra time if we do not write these additional lines. This is why we did not want to force the user to write an additional line at all.
- In our setting, the code in the try-block that is immediately executed will usually only contain coordination logic. Exceptions thrown by the tasks are thrown in threads of an `ExecutorService` and will be handled by the `ExecutionCoordinator`. They are not intractable. Hence, we will often be able to guarantee that that code inside the try-block does not throw exceptions at all. We can implement these cases without any additional lines and without time penalties.  

# Performance Overhead

Hippodamus is an abstraction layer built on top of standard Java concepts for parallelization. Consequently, there is some performance overhead compared to the lower-level mechanisms. We have added a few benchmark tests as unit tests (package `dd.kms.hippodamus.benchmark`) to ensure that this overhead is not dramatic. In these benchmark tests we compare the performance of Hippodamus, among others, to equivalent code based on the `CompletableFuture`-API.

Since Hippodamus manages dependencies between tasks to prevent tasks from actively waiting for other tasks, we identified two scenarios in which Hippodamus will compare particularly unfavourably to lower-level mechanisms:

1. If tasks do not depend on each other at all (`NoDependencyBenchmark.java`).
1. If tasks depend on each other, but these dependencies are not specified (`NoSpecifiedDependenciesBenchmark.java`).

In our tests it turned out that there is no significant difference between Hippodamus and equivalent `CompletableFuture` code with respect to performance.

Furthermore, we have added a benchmark test (`DependencyBenchmark`) where tasks depend on each other and specifying these dependencies should be beneficial. The results of this test confirm this expectation.

Note that these few benchmarks are just an indication that the performance overhead of Hippodamus is not significant. We do not provide a reliable statistics for that claim. We tried to write fair comparison code, but we cannot exclude the possibility that the code could be written to perform better.

# Open Source License Acknowledgement

Hippodamus utilizes [Guava: Google Core Libraries for Java](https://github.com/google/guava). This library is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
