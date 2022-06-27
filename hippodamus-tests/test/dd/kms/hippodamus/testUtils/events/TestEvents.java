package dd.kms.hippodamus.testUtils.events;

public class TestEvents
{
	public static final TestEvent	COORDINATOR_STARTED				= new UnparameterizedEvent("coordinator started");
	public static final TestEvent	COORDINATOR_STOPPED_EXTERNALLY	= new UnparameterizedEvent("coordinator stopped externally");
	public static final TestEvent	COORDINATOR_CLOSING				= new UnparameterizedEvent("coordinator closing");
	public static final TestEvent	COORDINATOR_CLOSED				= new UnparameterizedEvent("coordinator closed");

	public static TestEvent create(String displayString) {
		return new UnparameterizedEvent(displayString);
	}
}
