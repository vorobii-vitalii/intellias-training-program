package retry;

public class ThreadSleepWaiter implements Waiter {
	@Override
	public void wait(int timeInMillis) {
		try {
			Thread.sleep(timeInMillis);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
