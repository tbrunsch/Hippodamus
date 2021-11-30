package dd.kms.hippodamus.impl.handles;

import java.util.concurrent.Semaphore;

/**
 * Boolean value that can be waited for until true.<br/>
 * <br/>
 * Note that it the value is initially true and must be set to false manually after construction.
 */
class AwaitableBoolean
{
	private final	Semaphore	semaphore;

	/**
	 * A permit will be required on the semaphore if and only if the value is false.
	 */
	private boolean				value;

	AwaitableBoolean(Semaphore semaphore) {
		this.semaphore = semaphore;
		this.value = true;
	}

	synchronized void setFalse() throws InterruptedException {
		if (value) {
			semaphore.acquire();
			value = false;
		}
	}

	synchronized void setTrue() {
		if (!value) {
			semaphore.release();
			value = true;
		}
	}

	void waitUntilTrue() {
		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			return;
		}
		semaphore.release();
	}
}
