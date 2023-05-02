package tcp.server.handler;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import request_handler.NetworkRequest;
import tcp.server.ConnectionImpl;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;


public class GenericReadOperationHandler<T> implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(GenericReadOperationHandler.class);
	private final SocketMessageReader<T> socketMessageReader;
	private final BlockingQueue<NetworkRequest<T>> requestQueue;
	private final BiConsumer<SelectionKey, Throwable> onError;

	public GenericReadOperationHandler(
			BlockingQueue<NetworkRequest<T>> requestQueue,
			SocketMessageReader<T> socketMessageReader,
			BiConsumer<SelectionKey, Throwable> onError
	) {
		this.requestQueue = requestQueue;
		this.socketMessageReader = socketMessageReader;
		this.onError = onError;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var serverAttachment = ((ServerAttachment) selectionKey.attachment());
		try {
			var socketChannel = (SocketChannel) selectionKey.channel();
			var request = socketMessageReader.readMessage(serverAttachment.bufferContext(), socketChannel);
			if (request == null) {
				return;
			}
			requestQueue.put(new NetworkRequest<>(request, new ConnectionImpl(selectionKey)));
		} catch (Throwable e) {
			onError.accept(selectionKey, e);
		}
	}
}
