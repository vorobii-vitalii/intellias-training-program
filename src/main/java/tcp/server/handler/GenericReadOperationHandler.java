package tcp.server.handler;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.lmax.disruptor.RingBuffer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import request_handler.NetworkRequest;
import tcp.server.ConnectionImpl;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;

public class GenericReadOperationHandler<T> implements Consumer<SelectionKey> {
	private final SocketMessageReader<T> socketMessageReader;
	private final RingBuffer<NetworkRequest<T>> ringBuffer;
	private final BiConsumer<SelectionKey, Throwable> onError;
	private final Tracer readOperationHandlerTracer;
	private final String messageType;

	public GenericReadOperationHandler(
			RingBuffer<NetworkRequest<T>> ringBuffer,
			SocketMessageReader<T> socketMessageReader,
			BiConsumer<SelectionKey, Throwable> onError,
			OpenTelemetry openTelemetry,
			String messageType
	) {
		this.ringBuffer = ringBuffer;
		this.messageType = messageType;
		this.socketMessageReader = socketMessageReader;
		this.onError = onError;
		readOperationHandlerTracer = openTelemetry.getTracer("ReadOperationHandler");
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var serverAttachment = ((ServerAttachment) selectionKey.attachment());
		try {
			if (!serverAttachment.isReadable()) {
				return;
			}
			if (!selectionKey.isReadable()) {
				serverAttachment.getRequestSpan().addEvent("Not readable...");
				return;
			}
			var socketChannel = (SocketChannel) selectionKey.channel();
			var requestSpan = readOperationHandlerTracer
					.spanBuilder("Socket message")
					.setAttribute("message.type", messageType)
					.setAttribute("client.ip", socketChannel.getRemoteAddress().toString())
					.setParent(Context.current().with(serverAttachment.getRequestSpan()))
					.startSpan();
			var request = socketMessageReader.readMessage(serverAttachment.bufferContext(), socketChannel, requestSpan);
			if (request == null) {
				return;
			}
			requestSpan.addEvent("Message read from socket");
//			NetworkRequest<T> networkRequest = new NetworkRequest<>(request, new ConnectionImpl(serverAttachment), requestSpan);
			requestSpan.addEvent("Request created");
			var seq = ringBuffer.next();
			final NetworkRequest<T> networkRequest = ringBuffer.get(seq);
			networkRequest.setRequest(request);
			networkRequest.setSocketConnection(new ConnectionImpl(serverAttachment));
			networkRequest.setSpan(requestSpan);
			ringBuffer.publish(seq);
//			ringBuffer.add(networkRequest);
			requestSpan.end();
		} catch (CancelledKeyException cancelledKeyException) {
			// Ignore
		} catch (Throwable e) {
			onError.accept(selectionKey, e);
		}
	}
}
