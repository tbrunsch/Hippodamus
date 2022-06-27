package dd.kms.hippodamus.impl.handles;

import java.util.concurrent.Semaphore;

/**
 * Flag that can be waited for until set. Initially it is set.
 * <ul>
 *     <li>
 *         When the flag is changed from unset to set, then a permit is acquired from the underlying
 *         {@link Semaphore}.
 *     </li>
 *     <li>
 *         When the flag is changed from set to unset, then the acquired permit is released again.
 *     </li>
 *     <li>
 *         When setting or unsetting the flag twice subsequently, then nothing happens when the second
 *         set or unset is called. Particularly, at any moment at most one permit will be held from the
 *         underlying {@code Semaphore}.
 *     </li>
 * </ul>
 * <b>Important:</b> The user of this class must ensure that calls to {@link #set()} and {@link #unset()} are
 * not called concurrently.
 */
class AwaitableFlag
{
	private final Semaphore		semaphore;
	private volatile boolean	set;

	AwaitableFlag() {
		this(new Semaphore(1));
	}

	AwaitableFlag(Semaphore semaphore) {
		this.semaphore = semaphore;
		this.set = true;
	}

	/**
	 * Ensure that this method and {@link #set()} are not called concurrently.
	 */
	void unset() throws InterruptedException {
		if (set) {
			semaphore.acquire();
			set = false;
		}
	}

	/**
	 * Ensure that this method and {@link #unset()} are not called concurrently.
	 */
	void set() {
		if (!set) {
			semaphore.release();
			set = true;
		}
	}

	void waitUntilTrue() throws InterruptedException {
		semaphore.acquire();
		semaphore.release();
	}
}
