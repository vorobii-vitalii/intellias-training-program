package tcp.server.handler;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import request_handler.NetworkRequest;
import tcp.server.ConnectionImpl;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;

public class GenericReadOperationHandler<T> implements Consumer<SelectionKey> {
	private final SocketMessageReader<T> socketMessageReader;
	private final BlockingQueue<NetworkRequest<T>> requestQueue;
	private final BiConsumer<SelectionKey, Throwable> onError;
	private final Tracer readOperationHandlerTracer;

	public GenericReadOperationHandler(
			BlockingQueue<NetworkRequest<T>> requestQueue,
			SocketMessageReader<T> socketMessageReader,
			BiConsumer<SelectionKey, Throwable> onError,
			OpenTelemetry openTelemetry
	) {
		this.requestQueue = requestQueue;
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
					.setAttribute("client.ip", socketChannel.getRemoteAddress().toString())
					.setParent(Context.current().with(serverAttachment.getRequestSpan()))
					.startSpan();
			var request = socketMessageReader.readMessage(serverAttachment.bufferContext(), socketChannel, requestSpan);
			if (request == null) {
				return;
			}
			requestSpan.addEvent("Message read from socket");
			requestQueue.put(new NetworkRequest<>(request, new ConnectionImpl(serverAttachment), requestSpan));
			requestSpan.end();
		} catch (CancelledKeyException cancelledKeyException) {
			// Ignore
		} catch (Throwable e) {
			onError.accept(selectionKey, e);
		}
	}
}
