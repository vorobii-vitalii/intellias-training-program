package retry.strategy;

import retry.RetryExecutionFailedException;
import retry.RetryableExecutor;
import retry.Waiter;

public class ConstantDelayRetryExecutor implements RetryStrategy {
	private final int numAttempts;
	private final int waitBetweenAttempts;
	private final Waiter waiter;

	public ConstantDelayRetryExecutor(Waiter waiter, int numAttempts, int waitBetweenAttempts) {
		this.waiter = waiter;
		this.numAttempts = numAttempts;
		this.waitBetweenAttempts = waitBetweenAttempts;
	}

	@Override
	public <T> T execute(RetryableExecutor<T> retryableExecutor) throws RetryExecutionFailedException {
		for (var i = 0; i < numAttempts; i++) {
			try {
				return retryableExecutor.execute();
			} catch (Exception error) {
				error.printStackTrace();
			}
			if (i != numAttempts - 1) {
				waiter.wait(waitBetweenAttempts);
			}
		}
		throw new RetryExecutionFailedException();
	}
}
