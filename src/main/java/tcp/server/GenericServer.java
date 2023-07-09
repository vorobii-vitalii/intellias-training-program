package tcp.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class GenericServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(GenericServer.class);
	private static final int TIMEOUT = 1;
	private static final int BACKLOG = 10_000;
	private final ServerConfig serverConfig;
	private final Consumer<Throwable> errorHandler;
	private final SelectorProvider selectorProvider;
	private final Map<Integer, Consumer<SelectionKey>> operationHandlerByType;
	private Thread serverThread;
	private final ServerConfigurer serverConfigurer;

	public GenericServer(
			ServerConfig serverConfig,
			SelectorProvider selectorProvider,
			Consumer<Throwable> errorHandler,
			Map<Integer, Consumer<SelectionKey>> operationHandlerByType,
			ServerConfigurer serverConfigurer
	) {
		this.serverConfig = serverConfig;
		this.errorHandler = errorHandler;
		this.selectorProvider = selectorProvider;
		this.operationHandlerByType = operationHandlerByType;
		this.serverConfigurer = serverConfigurer;
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
			 var serverSocketChannel = serverConfigurer.configureServerChannel(selectorProvider, serverConfig)
		) {
			LOGGER.info("Starting server {}", serverConfig);
			serverSocketChannel.register(selector, serverConfig.getServerInterestOps());
			new Poller(selector, operationHandlerByType, serverConfig.getOnConnectionClose(), TIMEOUT).run();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
