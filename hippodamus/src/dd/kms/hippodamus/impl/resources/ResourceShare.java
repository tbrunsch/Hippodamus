package dd.kms.hippodamus.impl.resources;

public interface ResourceShare
{
	void addPendingResourceShare();
	void removePendingResourceShare();
	boolean tryAcquire(Runnable tryAgainRunnable);
	void release();
	void remove(Runnable tryAgainRunnable);
}
