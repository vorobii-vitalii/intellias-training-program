package document_editor;

import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.DocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;
import com.example.document.storage.TreePath;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.event.Event;
import document_editor.event.MessageDistributeEvent;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

// NGINX reconnect, Envoy proxy, EBPF
public class DocumentChangeWatcher implements Runnable {
    public static final int BATCH_SIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChangeWatcher.class);
    public static final int BATCH_TIMEOUT = 100;
    private final BlockingQueue<Event> eventQueue;
    private final ObjectMapper objectMapper;
    private final DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public DocumentChangeWatcher(
            BlockingQueue<Event> eventQueue,
             ObjectMapper objectMapper,
             DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService,
            OpenTelemetry openTelemetry
    ) {
        this.eventQueue = eventQueue;
        this.objectMapper = objectMapper;
        this.documentStorageService = documentStorageService;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("Document change watcher");
    }

    @Override
    public void run() {
        subscribeForDocumentChanges(null);
    }

    private void subscribeForDocumentChanges(String resumeToken) {
        var subscribeSpan = tracer.spanBuilder("Subscribe to document changes")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(Context.current())
                .startSpan();
        var scope = subscribeSpan.makeCurrent();
        AtomicReference<String> lastToken = new AtomicReference<>(resumeToken);
        var builder = SubscribeForDocumentChangesRequest.newBuilder()
                .setBatchSize(BATCH_SIZE)
                .setBatchTimeout(BATCH_TIMEOUT);
        if (resumeToken != null) {
            builder.setResumeToken(resumeToken);
        }
        documentStorageService.subscribeForDocumentsChanges(builder.build(), new StreamObserver<>() {
            @Override
            public void onNext(DocumentChangedEvents documentChangedEvents) {
                if (documentChangedEvents.getEventsCount() == 0) {
                    return;
                }
                subscribeSpan.addEvent("Received changes",
                        Attributes.of(AttributeKey.longKey("count"), (long) documentChangedEvents.getEventsCount()));
                distributeDocumentsChanges(documentChangedEvents);
                lastToken.set(documentChangedEvents.getEvents(documentChangedEvents.getEventsCount() - 1).getResumeToken());
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.warn("Error, reconnecting", throwable);
                subscribeSpan.end();
                scope.close();
                subscribeForDocumentChanges(lastToken.get());
            }

            @Override
            public void onCompleted() {
                LOGGER.info("Stream completed");
                subscribeSpan.end();
                scope.close();
            }
        });
    }

    private void distributeDocumentsChanges(DocumentChangedEvents documentChangedEvents) {
        try {
            var webSocketMessage = new WebSocketMessage();
            webSocketMessage.setFin(true);
            webSocketMessage.setOpCode(OpCode.BINARY);
            webSocketMessage.setPayload(objectMapper.writeValueAsBytes(new DocumentStreamingWebSocketEndpoint.Response(
                    DocumentStreamingWebSocketEndpoint.ResponseType.CHANGES,
                    documentChangedEvents
                            .getEventsList()
                            .stream()
                            .map(e -> {
                                var change = e.getChange();
                                return new PairDTO<>(
                                        toInternalPath(change.getPath()),
                                        change.hasCharacter() ? ((char) change.getCharacter()) : null
                                );
                            })
                            .collect(Collectors.toList())
            )));
            eventQueue.put(new MessageDistributeEvent(webSocketMessage));

        } catch (Throwable error) {
            LOGGER.warn("Error", error);
        }
    }

    private List<DocumentStreamingWebSocketEndpoint.TreePathEntry> toInternalPath(TreePath path) {
        return path.getEntriesList().stream()
                .map(entry -> new DocumentStreamingWebSocketEndpoint.TreePathEntry(
                        entry.getIsLeft(),
                        entry.getDisambiguator()
                ))
                .collect(Collectors.toList());
    }
}
