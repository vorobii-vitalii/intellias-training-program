package retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import retry.strategy.ConstantDelayRetryExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstantDelayRetryExecutorTest {
	public static final int NUM_ATTEMPTS = 3;
	public static final int WAIT_BETWEEN_ATTEMPTS = 100;
	public static final int RES = 100;
	@Mock
	private Waiter waiter;

	private ConstantDelayRetryExecutor retryExecutor;

	@BeforeEach
	void init() {
		retryExecutor = new ConstantDelayRetryExecutor(waiter, NUM_ATTEMPTS, WAIT_BETWEEN_ATTEMPTS);
	}

	@SuppressWarnings("unchecked")
	@Test
	void executeGivenOperationEventuallySucceeded() throws Exception {
		RetryableExecutor<Integer> retryableExecutor = mock(RetryableExecutor.class);
		when(retryableExecutor.execute())
						.thenThrow(new Exception())
						.thenThrow(new Exception())
						.thenReturn(RES);
		assertThat(retryExecutor.execute(retryableExecutor)).isEqualTo(RES);
		var inOrder = Mockito.inOrder(retryableExecutor, waiter);
		inOrder.verify(retryableExecutor).execute();
		inOrder.verify(waiter).wait(WAIT_BETWEEN_ATTEMPTS);
		inOrder.verify(retryableExecutor).execute();
		inOrder.verify(waiter).wait(WAIT_BETWEEN_ATTEMPTS);
		inOrder.verify(retryableExecutor).execute();
	}

	@SuppressWarnings("unchecked")
	@Test
	void executeGivenOperationNotSucceeded() throws Exception {
		RetryableExecutor<Integer> retryableExecutor = mock(RetryableExecutor.class);
		when(retryableExecutor.execute())
						.thenThrow(new Exception())
						.thenThrow(new Exception())
						.thenThrow(new Exception());
		assertThrows(RetryExecutionFailedException.class, () -> retryExecutor.execute(retryableExecutor));
	}

}
