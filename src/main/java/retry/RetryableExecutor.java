package retry;

public interface RetryableExecutor<T> {
	T execute() throws Exception;
}
