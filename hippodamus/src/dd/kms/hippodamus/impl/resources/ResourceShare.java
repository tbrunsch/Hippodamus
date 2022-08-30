package dd.kms.hippodamus.impl.resources;

public interface ResourceShare
{
	boolean tryAcquire(Runnable tryAgainRunnable);
	void release();
	void remove(Runnable tryAgainRunnable);
}
