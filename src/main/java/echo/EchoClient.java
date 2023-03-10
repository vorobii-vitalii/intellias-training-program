package echo;

import tcp.RetryableTCPClient;
import tcp.TCPClient;
import tcp.TCPClientImpl;
import tcp.TCPClientConfig;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;

public class EchoClient {
	private final TCPClient tcpClient;

	public EchoClient(TCPClientConfig tcpClientConfig, SelectorProvider selectorProvider) {
		this.tcpClient = new RetryableTCPClient(
						new TCPClientImpl(tcpClientConfig, selectorProvider),
						tcpClientConfig.getNumRetries(),
						tcpClientConfig.getWaitBeforeAttemptsInMilliseconds()
		);
	}

	public EchoConnection createConnection() throws IOException {
		return new EchoConnectionImpl(tcpClient.createConnection());
	}

}
