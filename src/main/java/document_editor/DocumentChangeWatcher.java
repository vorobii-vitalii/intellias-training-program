package document_editor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.RxDocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;

import document_editor.dto.Change;
import document_editor.dto.Changes;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.DocumentsEvent;
import document_editor.event.MessageDistributeDocumentsEvent;
import io.reactivex.Single;
import message_passing.MessageProducer;
import reactor.core.CoreSubscriber;
import serialization.Serializer;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

// NGINX reconnect, Envoy proxy, EBPF
public class DocumentChangeWatcher implements Runnable {
	protected static final int BATCH_SIZE = 100;
	protected static final int BATCH_TIMEOUT = 100;
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChangeWatcher.class);

	private final MessageProducer<DocumentsEvent> eventProducer;
	private final Serializer serializer;
	private final RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageService;

	public DocumentChangeWatcher(
			MessageProducer<DocumentsEvent> eventProducer,
			Serializer serializer,
			RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub documentStorageService
	) {
		this.eventProducer = eventProducer;
		this.serializer = serializer;
		this.documentStorageService = documentStorageService;
	}

	@Override
	public void run() {
		LOGGER.info("Starting watching for documents changes");
		subscribeForDocumentChanges(null);
	}

	private void subscribeForDocumentChanges(String resumeToken) {
		var lastToken = new AtomicReference<>(resumeToken);
		var builder = SubscribeForDocumentChangesRequest.newBuilder().setBatchSize(BATCH_SIZE).setBatchTimeout(BATCH_TIMEOUT);
		if (resumeToken != null) {
			builder.setResumeToken(resumeToken);
		}
		Single.just(builder.build())
				.flatMapPublisher(documentStorageService::subscribeForDocumentsChanges)
				.subscribe(new CoreSubscriber<>() {

					private Subscription subscription;

					@Override
					public void onSubscribe(Subscription subscription) {
						this.subscription = subscription;
						subscription.request(1);
					}

					@Override
					public void onNext(DocumentChangedEvents documentChangedEvents) {
						var eventsCount = documentChangedEvents.getEventsCount();
						LOGGER.info("Received event about updated documents - {} events", eventsCount);
						distributeDocumentsChanges(documentChangedEvents);
						lastToken.set(documentChangedEvents.getEvents(eventsCount - 1).getResumeToken());
						subscription.request(1);
					}

					@Override
					public void onError(Throwable error) {
						LOGGER.warn("Error, reconnecting", error);
						subscribeForDocumentChanges(lastToken.get());
					}

					@Override
					public void onComplete() {
						LOGGER.info("Stream completed");
					}
				});
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
			LOGGER.warn("Serialization error", error);
		}
	}

	private Response getResponse(DocumentChangedEvents documentChangedEvents) {
		return new Response(ResponseType.CHANGES, new Changes(calculateChanges(documentChangedEvents), false, "event"));
	}

	private List<Change> calculateChanges(DocumentChangedEvents documentChangedEvents) {
		return documentChangedEvents
				.getEventsList()
				.stream()
				.map(e -> {
					var change = e.getChange();
					return new Change(
							change.getCharId(),
							change.getParentCharId(),
							change.getIsRight(),
							change.getDisambiguator(),
							change.hasCharacter() ? ((char) change.getCharacter()) : null
					);
				})
				.collect(Collectors.toList());
	}
}
