# Hippodamus
Hippodamus is a Java library for ...

Open Points:

- Nursery + Dependency Management + Exception Handling
- Explain entry point: Coordinators
- Samples comparing it to CompletableFutures (among others: transaction)
- explain Exception Handling Magic; internal exceptions preferred over task exceptions
- Execution vs. Aggregation
- Completion and Exception Listeners: discourage, explain how to use
- Aggregtion: Demonstrate usage, default aggregators, short circuit evaluation
- Support for Runnable + Callable
- StoppableExceptionalRunnable and StoppableExceptionalCallable: Why?
- permitTaskSubmission: Usage for unexpected exceptions
- coordinator configuration: custom executor services (and defaults), logging, dependency verification
- execution configuration: naming (-> logging, query via Handle.getTaskName()), task types (-> executor services), dependencies

# Target
...

# When to use Hippodamus

# Open Source License Acknowledgement

Hippodamus utilizes [Guava: Google Core Libraries for Java](https://github.com/google/guava). This library is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
