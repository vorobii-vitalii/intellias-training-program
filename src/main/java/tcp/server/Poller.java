package tcp.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelector;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Poller implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(Poller.class);

	private final Selector selector;
	private final Map<Integer, Consumer<SelectionKey>> operationHandlerByType;
	private final Consumer<SelectionKey> onSelectionKeyInvalidation;
	private final int selectionTimeout;

	public Poller(
			Selector selector,
			Map<Integer, Consumer<SelectionKey>> operationHandlerByType,
			Consumer<SelectionKey> onSelectionKeyInvalidation,
			int selectionTimeout
	) {
		this.selector = selector;
		this.operationHandlerByType = operationHandlerByType;
		this.onSelectionKeyInvalidation = onSelectionKeyInvalidation;
		this.selectionTimeout = selectionTimeout;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				selector.select(selectionKey -> {
					try {
						if (!selectionKey.isValid()) {
							onSelectionKeyInvalidation.accept(selectionKey);
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
				}, selectionTimeout);
			}
			catch (IOException e) {
				LOGGER.error("Error", e);
			}
		}
	}
}
