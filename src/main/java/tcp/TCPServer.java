package tcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.function.Consumer;

public class TCPServer {
	private final TCPServerConfig serverConfig;
	private final Consumer<Throwable> errorHandler;
	private final SelectorProvider selectorProvider;
	private final Map<ServerOperationType, Consumer<SelectionKey>> operationHandlerByType;
	private Thread serverThread;

	public TCPServer(
			TCPServerConfig serverConfig,
			SelectorProvider selectorProvider,
			Consumer<Throwable> errorHandler,
			Map<ServerOperationType, Consumer<SelectionKey>> operationHandlerByType
	) {
		this.serverConfig = serverConfig;
		this.errorHandler = errorHandler;
		this.selectorProvider = selectorProvider;
		this.operationHandlerByType = operationHandlerByType;
	}

	public synchronized void start() {
		if (serverThread != null) {
			return;
		}
		serverThread = new Thread(this::runServer);
		serverThread.setUncaughtExceptionHandler((thread, error) -> errorHandler.accept(error));
		serverThread.start();
	}

	public synchronized void stop(int timeout) throws InterruptedException {
		if (serverThread == null) {
			return;
		}
		serverThread.interrupt();
		serverThread.join(timeout);
		serverThread = null;
	}

	private void runServer() {
		try (var selector = selectorProvider.openSelector();
				var serverSocketChannel = selectorProvider.openServerSocketChannel(serverConfig.getProtocolFamily())
		) {
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			serverSocketChannel.bind(new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort()));
			while (!Thread.currentThread().isInterrupted()) {
				selector.select(selectionKey -> {
					var operationType = calcOperationType(selectionKey);
					var operationHandler = operationHandlerByType.get(operationType);
					if (operationHandler != null) {
						operationHandler.accept(selectionKey);
					}
				});
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private ServerOperationType calcOperationType(SelectionKey selectionKey) {
		if (selectionKey.isAcceptable()) {
			return ServerOperationType.ACCEPT;
		} else if (selectionKey.isWritable()) {
			return ServerOperationType.WRITE;
		} else if (selectionKey.isReadable()) {
			return ServerOperationType.READ;
		}
		return null;
	}

}
