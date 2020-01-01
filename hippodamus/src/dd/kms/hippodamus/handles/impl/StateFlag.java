package dd.kms.hippodamus.handles.impl;

enum StateFlag
{
	SUBMITTED	("submitting...",	"submitted"),
	COMPLETED	("completing...",	"completed"),
	STOPPED		("stopping...",		"stopped");

	private final String	transactionBeginString;
	private final String	transactionEndString;

	StateFlag(String transactionBeginString, String transactionEndString) {
		this.transactionBeginString = transactionBeginString;
		this.transactionEndString = transactionEndString;
	}

	String getTransactionBeginString() {
		return transactionBeginString;
	}

	String getTransactionEndString() {
		return transactionEndString;
	}
}
