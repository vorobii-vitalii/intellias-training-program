package tcp.server.handler;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import message_passing.MessageProducer;
import request_handler.NetworkRequest;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;

public class GenericReadOperationHandler<T> implements Consumer<SelectionKey> {
	private final SocketMessageReader<T> socketMessageReader;
	private final MessageProducer<NetworkRequest<T>> messageProducer;
	private final BiConsumer<SelectionKey, Throwable> onError;
	private final Tracer tracer;
	private final Supplier<Context> contextSupplier;

	public GenericReadOperationHandler(
			MessageProducer<NetworkRequest<T>> messageProducer,
			SocketMessageReader<T> socketMessageReader,
			BiConsumer<SelectionKey, Throwable> onError,
			Tracer tracer,
			Supplier<Context> contextSupplier
	) {
		this.messageProducer = messageProducer;
		this.tracer = tracer;
		this.socketMessageReader = socketMessageReader;
		this.onError = onError;
		this.contextSupplier = contextSupplier;
	}

	@Override
	public void accept(SelectionKey selectionKey) {
		var serverAttachment = ((ServerAttachment) selectionKey.attachment());
		try {
			if (!serverAttachment.isReadable()) {
				return;
			}
			var requestSpan = tracer
					.spanBuilder("Socket message")
					.setParent(contextSupplier.get().with(serverAttachment.getRequestSpan()))
					.startSpan();
			var request = socketMessageReader.readMessage(
					serverAttachment.bufferContext(),
					serverAttachment.getChannel(),
					requestSpan::addEvent
			);
			if (request == null) {
				return;
			}
			requestSpan.addEvent("Message read from socket");
			messageProducer.produce(new NetworkRequest<>(request, serverAttachment.toSocketConnection()));
			requestSpan.end();
		} catch (CancelledKeyException cancelledKeyException) {
			// Ignore
		} catch (Throwable e) {
			onError.accept(selectionKey, e);
		}
	}
}
