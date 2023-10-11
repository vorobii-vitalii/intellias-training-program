package tcp.server.handler;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import message_passing.MessageProducer;
import request_handler.NetworkRequest;
import tcp.server.ServerAttachment;
import tcp.server.SocketMessageReader;
import tcp.server.reader.exception.ParseException;

public class GenericReadOperationHandler<T> implements Consumer<SelectionKey> {
	private static final Logger LOGGER = LoggerFactory.getLogger(GenericReadOperationHandler.class);

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
			var requestSpan = createRequestSpan(serverAttachment);
			do {
				try {
					var request = socketMessageReader.readMessage(
							serverAttachment.bufferContext(),
							serverAttachment.getChannel(),
							requestSpan::addEvent
					);
					if (request == null) {
						break;
					}
					requestSpan.addEvent("Message read from socket");
					messageProducer.produce(new NetworkRequest<>(request, serverAttachment.toSocketConnection()));
				}
				catch (ParseException parseException) {
					LOGGER.warn("Parse exception", parseException);
					break;
				}
			}
			while (!serverAttachment.bufferContext().isEmpty());
			requestSpan.end();
		} catch (CancelledKeyException cancelledKeyException) {
			// Ignore
		} catch (Throwable e) {
			onError.accept(selectionKey, e);
		}
	}

	private Span createRequestSpan(ServerAttachment serverAttachment) {
		var requestSpan = serverAttachment.getRequestSpan();
		return tracer
				.spanBuilder("Socket message")
				.setParent(requestSpan == null ? contextSupplier.get() : contextSupplier.get().with(requestSpan))
				.startSpan();
	}
}
