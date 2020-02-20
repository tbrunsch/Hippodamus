package dd.kms.hippodamus.logging;

import dd.kms.hippodamus.coordinator.configuration.ExecutionCoordinatorBuilder;
import dd.kms.hippodamus.handles.Handle;
import dd.kms.hippodamus.handles.ResultHandle;

public enum LogLevel
{
	/**
	 * Messages on this level describe internal errors of the parallelization framework or
	 * its application by the programmer. The latter is the case, e.g., if the programmer
	 * tells the framework to verify dependencies (cf. {@link ExecutionCoordinatorBuilder#verifyDependencies(boolean)}))
	 * and some task X tries to access the value of a {@link ResultHandle} of
	 * another task Y task X depends but that has not completed yet. This indicates either an
	 * error in the framework or it means that the programmer forgot to specify that
	 * task X depends on task Y.<br/>
	 * <br/>
	 * Note that exceptions that are thrown by the tasks itself are not considered as
	 * internal errors and reported differently.
	 */
	INTERNAL_ERROR,

	/**
	 * Messages on this level describe state changes of {@link Handle}s.
	 */
	STATE
}
