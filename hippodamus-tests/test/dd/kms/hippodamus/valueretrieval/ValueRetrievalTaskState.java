package dd.kms.hippodamus.valueretrieval;

import java.util.List;

/**
 * This enum describes the state a task is in when its value is retrieved. The five values
 * conform to the states discussed in the README.
 */
enum ValueRetrievalTaskState
{
	NOT_YET_EXECUTED(false),
	EXECUTING(false),
	STOPPED_BEFORE_TERMINATION(true),
	TERMINATED_REGULARLY(true),
	TERMINATED_EXCEPTIONALLY(true);

	private final boolean readyToJoin;

	ValueRetrievalTaskState(boolean readyToJoin) {
		this.readyToJoin = readyToJoin;
	}

	boolean isReadyToJoin() {
		return readyToJoin;
	}
}
