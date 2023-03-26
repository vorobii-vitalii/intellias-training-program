package retry.strategy;

import retry.RetryExecutionFailedException;
import retry.RetryableExecutor;

public interface RetryStrategy {
	<T> T execute(RetryableExecutor<T> supplier) throws RetryExecutionFailedException;
}
