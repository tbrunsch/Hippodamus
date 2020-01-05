package dd.kms.hippodamus.exceptions;

/* TODO:
Zusätzlich zu diesem Interface sollte es auch ein Interface geben, dass ein ReadableValue<Boolean> (Stopflag) als Methodenargument erhält.
Die Methode erhält dann den hasStopped-Getter seines Handles.
 */

@FunctionalInterface
public interface ExceptionalCallable<V, E extends Throwable>
{
	V call() throws E;
}
