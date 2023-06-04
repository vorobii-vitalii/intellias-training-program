package document_editor.event.handler.impl;

import com.example.document.storage.Change;
import com.example.document.storage.ChangesRequest;
import com.example.document.storage.ChangesResponse;
import com.example.document.storage.DocumentStorageServiceGrpc;
import document_editor.HttpServer;
import document_editor.dto.ConnectDocumentReply;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.DocumentsEventType;
import document_editor.event.EditDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import grpc.ServiceDecorator;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import serialization.Serializer;
import tcp.MessageSerializer;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

import java.io.IOException;
import java.util.stream.Collectors;

public class EditEventHandler implements EventHandler<EditDocumentsEvent> {
	private static final Logger LOGGER = LoggerFactory.getLogger(EditEventHandler.class);

	private final DocumentStorageServiceGrpc.DocumentStorageServiceStub service;
	private final Tracer tracer;
	private final ServiceDecorator serviceDecorator;
	private final MessageSerializer messageSerializer;
	private final Serializer serializer;

	public EditEventHandler(
			DocumentStorageServiceGrpc.DocumentStorageServiceStub service,
			Tracer tracer,
			ServiceDecorator serviceDecorator,
			MessageSerializer messageSerializer,
			Serializer serializer
	) {
		this.service = service;
		this.tracer = tracer;
		this.serviceDecorator = serviceDecorator;
		this.messageSerializer = messageSerializer;
		this.serializer = serializer;
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
				.setParent(Context.current().with(event.socketConnection().getSpan()))
				.startSpan();

		var scope = applyChangesSpan.makeCurrent();
		serviceDecorator.decorateService(service)
				.applyChanges(ChangesRequest.newBuilder().addAllChanges(changes).build(), new StreamObserver<>() {
					@Override
					public void onNext(ChangesResponse changesResponse) {
						sendAcknowledgement(event.socketConnection(), event.changeId(), ResponseType.ACK);
					}

					@Override
					public void onError(Throwable throwable) {
						sendAcknowledgement(event.socketConnection(), event.changeId(), ResponseType.NACK);
						LOGGER.error("Error on inserts", throwable);
					}

					@Override
					public void onCompleted() {
						LOGGER.debug("Operation completed");
						applyChangesSpan.end();
						scope.close();
					}
				});
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
