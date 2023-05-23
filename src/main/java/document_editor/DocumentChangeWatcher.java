package document_editor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.DocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.dto.Change;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.dto.TreePathDTO;
import document_editor.event.Event;
import document_editor.event.MessageDistributeEvent;
import grpc.TracingContextPropagator;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

// NGINX reconnect, Envoy proxy, EBPF
public class DocumentChangeWatcher implements Runnable {
	public static final int BATCH_SIZE = 100;
	public static final int BATCH_TIMEOUT = 100;
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChangeWatcher.class);
	private final Queue<Event> eventQueue;
	private final ObjectMapper objectMapper;
	private final DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService;
	private final OpenTelemetry openTelemetry;

	public DocumentChangeWatcher(
			Queue<Event> eventQueue,
			ObjectMapper objectMapper,
			DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService,
			OpenTelemetry openTelemetry
	) {
		this.eventQueue = eventQueue;
		this.objectMapper = objectMapper;
		this.documentStorageService = documentStorageService;
		this.openTelemetry = openTelemetry;
	}

	@Override
	public void run() {
		subscribeForDocumentChanges(null);
	}

	private byte[] serialize(Object obj) throws IOException {
		var arrayOutputStream = new ByteArrayOutputStream();
		objectMapper.writeValue(new GZIPOutputStream(arrayOutputStream), obj);
		return arrayOutputStream.toByteArray();
	}

	private void subscribeForDocumentChanges(String resumeToken) {
		AtomicReference<String> lastToken = new AtomicReference<>(resumeToken);
		var builder = SubscribeForDocumentChangesRequest.newBuilder()
				.setBatchSize(BATCH_SIZE)
				.setBatchTimeout(BATCH_TIMEOUT);
		if (resumeToken != null) {
			builder.setResumeToken(resumeToken);
		}
		documentStorageService
				.withCallCredentials(new TracingContextPropagator(Context.current(), openTelemetry))
				.subscribeForDocumentsChanges(builder.build(),
						new StreamObserver<>() {
							@Override
							public void onNext(DocumentChangedEvents documentChangedEvents) {
								if (documentChangedEvents.getEventsCount() == 0) {
									return;
								}
								distributeDocumentsChanges(documentChangedEvents);
								lastToken.set(documentChangedEvents.getEvents(documentChangedEvents.getEventsCount() - 1).getResumeToken());
							}

							@Override
							public void onError(Throwable throwable) {
								LOGGER.warn("Error, reconnecting", throwable);
								subscribeForDocumentChanges(lastToken.get());
							}

							@Override
							public void onCompleted() {
								LOGGER.info("Stream completed");
							}
						});
	}

	private void distributeDocumentsChanges(DocumentChangedEvents documentChangedEvents) {
		try {
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			webSocketMessage.setOpCode(OpCode.BINARY);
			webSocketMessage.setPayload(serialize(new Response(
					ResponseType.CHANGES,
					documentChangedEvents
							.getEventsList()
							.stream()
							.map(e -> {
								var change = e.getChange();
								return new Change(
										toInternalPath(change),
										change.hasCharacter() ? ((char) change.getCharacter()) : null
								);
							})
							.collect(Collectors.toList())
			)));
			eventQueue.add(new MessageDistributeEvent(webSocketMessage));

		}
		catch (Throwable error) {
			LOGGER.warn("Error", error);
		}
	}

	private TreePathDTO toInternalPath(com.example.document.storage.Change event) {
		return new TreePathDTO(event.getDirectionsList(), event.getDisambiguatorsList());
	}
}
