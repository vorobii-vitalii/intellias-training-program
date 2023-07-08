package echo.server;

import echo.handler.EchoAcceptOperationHandler;
import echo.handler.EchoReadOperationHandler;
import echo.handler.EchoWriteOperationHandler;
import tcp.server.GenericServer;
import tcp.server.ServerConfig;
import tcp.server.TCPServerConfigurer;

import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.function.Consumer;

import static java.nio.channels.SelectionKey.*;

public class EchoServer {
	private final GenericServer genericServer;

	public EchoServer(
			ServerConfig serverConfig,
			SelectorProvider selectorProvider,
			Consumer<Throwable> errorHandler,
			int bufferCapacity
	) {
		genericServer = new GenericServer(
				serverConfig,
				selectorProvider,
				errorHandler,
				Map.of(
						OP_ACCEPT, new EchoAcceptOperationHandler(bufferCapacity),
						OP_READ, new EchoReadOperationHandler(),
						OP_WRITE, new EchoWriteOperationHandler()
				),
				new TCPServerConfigurer());
	}

	public void start() {
		genericServer.start();
	}
}
