package tcp.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TCPServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TCPServer.class);
	public static final int TIMEOUT = 100;
	private final TCPServerConfig serverConfig;
	private final Consumer<Throwable> errorHandler;
	private final SelectorProvider selectorProvider;
	private final Map<Integer, Consumer<SelectionKey>> operationHandlerByType;
	private Thread serverThread;

	public TCPServer(
			TCPServerConfig serverConfig,
			SelectorProvider selectorProvider,
			Consumer<Throwable> errorHandler,
			Map<Integer, Consumer<SelectionKey>> operationHandlerByType
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
		serverThread.setName("TCP Server");
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
					try {
						if (!selectionKey.isValid()) {
							serverConfig.getOnConnectionClose().accept(selectionKey);
							return;
						}
						var operationHandler = operationHandlerByType.get(selectionKey.readyOps());
						if (operationHandler != null) {
							operationHandler.accept(selectionKey);
						}
					}
					catch (Throwable error) {
						LOGGER.error("Error", error);
					}
				}, TIMEOUT);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
