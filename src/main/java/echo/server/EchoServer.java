package echo.server;

import echo.handler.EchoAcceptOperationHandler;
import echo.handler.EchoReadOperationHandler;
import echo.handler.EchoWriteOperationHandler;
import tcp.server.TCPServer;
import tcp.server.TCPServerConfig;

import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.function.Consumer;

import static java.nio.channels.SelectionKey.*;

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
					OP_ACCEPT, new EchoAcceptOperationHandler(bufferCapacity),
					OP_READ, new EchoReadOperationHandler(),
					OP_WRITE, new EchoWriteOperationHandler()
			));
	}

	public void start() {
		tcpServer.start();
	}
}
