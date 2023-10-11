package tcp.client;

import retry.RetryExecutionFailedException;
import retry.strategy.RetryStrategy;

import java.io.IOException;

public class RetryableTCPClient implements TCPClient {
	private final TCPClient tcpClient;
	private final RetryStrategy retryStrategy;

	public RetryableTCPClient(TCPClient tcpClient, RetryStrategy retryStrategy) {
		this.tcpClient = tcpClient;
		this.retryStrategy = retryStrategy;
	}

	@Override
	public TCPConnection createConnection() throws IOException {
		try {
			return retryStrategy.execute(tcpClient::createConnection);
		} catch (RetryExecutionFailedException e) {
			throw new IOException("Connection couldn't be established after several retries");
		}
	}
}
