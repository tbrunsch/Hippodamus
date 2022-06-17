package dd.kms.hippodamus.testUtils.events;

class UnparameterizedEvent extends TestEvent
{
	private final String	displayString;

	UnparameterizedEvent(String displayString) {
		this.displayString = displayString;
	}

	@Override
	public boolean equals(Object o) {
		return o == this;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}

	@Override
	public String toString() {
		return displayString;
	}
}
