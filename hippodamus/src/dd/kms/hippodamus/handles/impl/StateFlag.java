package dd.kms.hippodamus.handles.impl;

enum StateFlag
{
	SUBMITTED	(0, "submitting...",	"submitted"),
	COMPLETED	(1, "completing...",	"completed"),
	STOPPED		(2, "stopping...",		"stopped");

	private final int		bitMask;
	private final String	transactionBeginString;
	private final String	transactionEndString;

	StateFlag(int bit, String transactionBeginString, String transactionEndString) {
		this.bitMask = 1 << bit;
		this.transactionBeginString = transactionBeginString;
		this.transactionEndString = transactionEndString;
	}

	String getTransactionBeginString() {
		return transactionBeginString;
	}

	String getTransactionEndString() {
		return transactionEndString;
	}

	int getBitMask() {
		return bitMask;
	}
}
