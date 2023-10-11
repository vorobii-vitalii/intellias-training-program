package document_editor.event.handler.impl;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.Change;
import com.example.document.storage.ChangesRequest;
import com.example.document.storage.RxDocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.DocumentsEventType;
import document_editor.event.EditDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import grpc.ServiceDecorator;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.reactivex.Single;
import serialization.Serializer;
import tcp.server.impl.MessageSerializerImpl;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class EditEventHandler implements EventHandler<EditDocumentsEvent> {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditEventHandler.class);

	private final RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub service;
	private final Tracer tracer;
	private final ServiceDecorator serviceDecorator;
	private final MessageSerializerImpl messageSerializer;
	private final Serializer serializer;
	private final Supplier<Context> contextSupplier;

	public EditEventHandler(
			RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub service,
			Tracer tracer,
			ServiceDecorator serviceDecorator,
			MessageSerializerImpl messageSerializer,
			Serializer serializer,
			Supplier<Context> contextSupplier
	) {
		this.service = service;
		this.tracer = tracer;
		this.serviceDecorator = serviceDecorator;
		this.messageSerializer = messageSerializer;
		this.serializer = serializer;
		this.contextSupplier = contextSupplier;
	}

	@Override
	public DocumentsEventType<EditDocumentsEvent> getHandledEventType() {
		return DocumentsEventType.EDIT;
	}

	@Override
	public void handle(EditDocumentsEvent event, ClientConnectionsContext clientConnectionsContext) {
		var changes = event.changes().stream()
				.map(c -> {
					var builder = Change.newBuilder()
							.setDocumentId(HttpServer.DOCUMENT_ID)
							.setCharId(c.charId())
							.setIsRight(c.isRight())
							.setDisambiguator(c.disambiguator());
					if (c.parentCharId() != null) {
						builder.setParentCharId(c.parentCharId());
					}
					if (c.character() != null) {
						builder.setCharacter(c.character());
					}
					return builder.build();
				})
				.collect(Collectors.toList());
		LOGGER.debug("Applying changes {}", changes);
		var applyChangesSpan = tracer.spanBuilder("Apply documents changes")
				.setSpanKind(SpanKind.CLIENT)
				.setParent(contextSupplier.get().with(event.socketConnection().getSpan()))
				.startSpan();

		var scope = applyChangesSpan.makeCurrent();

		Single.just(ChangesRequest.newBuilder().addAllChanges(changes).build())
				.compose(c -> serviceDecorator.decorateService(service).applyChanges(c))
				.doOnSuccess(r -> {
					sendAcknowledgement(event.socketConnection(), event.changeId(), ResponseType.ACK);
				})
				.doOnError(err -> {
					LOGGER.error("Error on inserts", err);
					sendAcknowledgement(event.socketConnection(), event.changeId(), ResponseType.NACK);
				})
				.doOnDispose(() -> {
					LOGGER.debug("Operation completed");
					applyChangesSpan.end();
					scope.close();
				})
				.subscribe();
	}

	private void sendAcknowledgement(SocketConnection connection, String changeId, ResponseType responseType) {
		var webSocketMessage = new WebSocketMessage();
		webSocketMessage.setFin(true);
		try {
			webSocketMessage.setPayload(serializer.serialize(new Response(responseType, changeId)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		webSocketMessage.setOpCode(OpCode.BINARY);
		try {
			connection.appendResponse(messageSerializer.serialize(webSocketMessage));
			connection.changeOperation(OperationType.WRITE);
		} catch (Exception e) {
			LOGGER.error("Failed to send acknowledgement because of", e);
		}
	}

}
