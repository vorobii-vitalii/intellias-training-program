package http.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tcp.server.BufferContext;
import tcp.server.ServerAttachment;
import util.Constants;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class HTTPAcceptOperationHandler implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(HTTPAcceptOperationHandler.class);

	@Override
	public void accept(SelectionKey selectionKey) {
		try {
			var socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
			var selector = selectionKey.selector();
			LOGGER.info("Accepted new connection {}", socketChannel);
			socketChannel.configureBlocking(false);
			socketChannel.register(
							selector,
							SelectionKey.OP_READ,
							new ServerAttachment(
								Constants.Protocol.HTTP,
								new BufferContext(),
								new LinkedBlockingQueue<>(),
								new ConcurrentHashMap<>()
							));
		} catch (IOException e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}

}
