# Changelog

## v0.2.0

API changes:
  * changed package structure: all API classes are now in the package `dd.kms.hippodamus.api`
  * `ExecutionCoordinatorBuilder` does not have a generic parameter anymore
  * `ExecutionConfigurationBuilder` does not have a generic parameter anymore
  * task types are no `int`s anymore, but instances of `TaskType`
  * the predefined task types have been renamed from "REGULAR" to "COMPUTATIONAL" and from "IO" to "BLOCKING" (motivated by the post [Be Aware of ForkJoinPool#commonPool()](https://dzone.com/articles/be-aware-of-forkjoinpoolcommonpool])
  * removed method `Handle.submit()` because it is only for internal use only

## v0.1.0

First Hippodamus release