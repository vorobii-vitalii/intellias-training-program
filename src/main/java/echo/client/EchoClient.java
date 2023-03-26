package echo.client;

import echo.connection.EchoConnection;
import echo.connection.EchoConnectionImpl;
import retry.RetryableExecutor;
import retry.strategy.ConstantDelayRetryExecutor;
import retry.Waiter;
import retry.strategy.RetryStrategy;
import tcp.client.RetryableTCPClient;
import tcp.client.TCPClient;
import tcp.client.TCPClientConfig;
import tcp.client.TCPClientImpl;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;

public class EchoClient {
	private final TCPClient tcpClient;

	public EchoClient(TCPClientConfig tcpClientConfig, SelectorProvider selectorProvider, RetryStrategy retryStrategy) {
		this.tcpClient = new RetryableTCPClient(
						new TCPClientImpl(tcpClientConfig, selectorProvider),
						retryStrategy
		);
	}

	public EchoConnection createConnection() throws IOException {
		return new EchoConnectionImpl(tcpClient.createConnection());
	}

}
