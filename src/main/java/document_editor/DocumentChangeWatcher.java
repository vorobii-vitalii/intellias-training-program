package document_editor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.DocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;

import document_editor.dto.Change;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.dto.TreePathDTO;
import document_editor.event.DocumentsEvent;
import document_editor.event.MessageDistributeDocumentsEvent;
import io.grpc.stub.StreamObserver;
import message_passing.MessageProducer;
import serialization.Serializer;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

// NGINX reconnect, Envoy proxy, EBPF
public class DocumentChangeWatcher implements Runnable {
	private static final int BATCH_SIZE = 100;
	private static final int BATCH_TIMEOUT = 100;
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChangeWatcher.class);
	private final MessageProducer<DocumentsEvent> eventProducer;
	private final Serializer serializer;
	private final DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService;

	public DocumentChangeWatcher(
			MessageProducer<DocumentsEvent> eventProducer,
			Serializer serializer,
			DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService
	) {
		this.eventProducer = eventProducer;
		this.serializer = serializer;
		this.documentStorageService = documentStorageService;
	}

	@Override
	public void run() {
		subscribeForDocumentChanges(null);
	}

	private void subscribeForDocumentChanges(String resumeToken) {
		var lastToken = new AtomicReference<>(resumeToken);
		var builder = SubscribeForDocumentChangesRequest.newBuilder().setBatchSize(BATCH_SIZE).setBatchTimeout(BATCH_TIMEOUT);
		if (resumeToken != null) {
			builder.setResumeToken(resumeToken);
		}
		var streamCompleted = new StreamObserver<DocumentChangedEvents>() {
			@Override
			public void onNext(DocumentChangedEvents documentChangedEvents) {
				distributeDocumentsChanges(documentChangedEvents);
				lastToken.set(documentChangedEvents.getEvents(documentChangedEvents.getEventsCount() - 1).getResumeToken());
			}

			@Override
			public void onError(Throwable throwable) {
				LOGGER.warn("Error, reconnecting", throwable);
			}

			@Override
			public void onCompleted() {
				LOGGER.info("Stream completed");
				subscribeForDocumentChanges(lastToken.get());
			}
		};
		documentStorageService.subscribeForDocumentsChanges(builder.build(), streamCompleted);
	}

	private void distributeDocumentsChanges(DocumentChangedEvents documentChangedEvents) {
		try {
			var webSocketMessage = new WebSocketMessage();
			webSocketMessage.setFin(true);
			webSocketMessage.setOpCode(OpCode.BINARY);
			webSocketMessage.setPayload(serializer.serialize(getResponse(documentChangedEvents)));
			eventProducer.produce(new MessageDistributeDocumentsEvent(webSocketMessage));
		}
		catch (Throwable error) {
			LOGGER.warn("Error", error);
		}
	}

	private Response getResponse(DocumentChangedEvents documentChangedEvents) {
		return new Response(ResponseType.CHANGES, calculateChanges(documentChangedEvents));
	}

	private List<Change> calculateChanges(DocumentChangedEvents documentChangedEvents) {
		return documentChangedEvents
				.getEventsList()
				.stream()
				.map(e -> {
					var change = e.getChange();
					return new Change(
							toInternalPath(change),
							change.hasCharacter() ? ((char) change.getCharacter()) : null
					);
				})
				.collect(Collectors.toList());
	}

	private TreePathDTO toInternalPath(com.example.document.storage.Change event) {
		return new TreePathDTO(event.getDirectionsList(), event.getDisambiguatorsList());
	}
}
