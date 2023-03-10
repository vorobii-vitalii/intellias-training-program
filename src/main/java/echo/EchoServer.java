package echo;

import tcp.ServerOperationType;
import tcp.TCPServer;
import tcp.TCPServerConfig;

import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.function.Consumer;

public class EchoServer {
	private final TCPServer tcpServer;

	public EchoServer(
		TCPServerConfig serverConfig,
		SelectorProvider selectorProvider,
		Consumer<Throwable> errorHandler,
		int bufferCapacity
	) {
		tcpServer = new TCPServer(
			serverConfig,
			selectorProvider,
			errorHandler,
			Map.of(
					ServerOperationType.ACCEPT, new EchoAcceptOperationHandler(bufferCapacity),
					ServerOperationType.READ, new EchoReadOperationHandler(),
					ServerOperationType.WRITE, new EchoWriteOperationHandler()
			));
	}

	public void start() {
		tcpServer.start();
	}

	public void stop(int timeout) throws InterruptedException {
		tcpServer.stop(timeout);
	}

}
