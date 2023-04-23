package tcp.server.handler;

import request_handler.NetworkRequest;
import tcp.server.ConnectionImpl;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public class GenericReadOperationHandler<T> implements Consumer<SelectionKey> {
	private final SocketMessageReader<T> socketMessageReader;
	private final BlockingQueue<NetworkRequest<T>> requestQueue;

	public GenericReadOperationHandler(
					BlockingQueue<NetworkRequest<T>> requestQueue,
					SocketMessageReader<T> socketMessageReader
	) {
		this.requestQueue = requestQueue;
		this.socketMessageReader = socketMessageReader;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var serverAttachment = ((ServerAttachment) selectionKey.attachment());
		try {
			var socketChannel = (SocketChannel) selectionKey.channel();
			var request = socketMessageReader.readMessage(serverAttachment.bufferContext(), socketChannel);
			if (request != null) {
				requestQueue.add(new NetworkRequest<>(request, new ConnectionImpl(selectionKey)));
			}
		}
		catch (Exception e) {
			selectionKey.cancel();
			e.printStackTrace();
		}
	}
}
