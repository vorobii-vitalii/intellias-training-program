package document_editor;

import com.example.document.storage.DocumentChangedEvents;
import com.example.document.storage.DocumentStorageServiceGrpc;
import com.example.document.storage.SubscribeForDocumentChangesRequest;
import com.example.document.storage.TreePath;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import document_editor.event.Event;
import document_editor.event.MessageDistributeEvent;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

// NGINX reconnect, Envoy proxy, EBPF
public class DocumentChangeWatcher implements Runnable {
    public static final int BATCH_SIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentChangeWatcher.class);
    public static final int BATCH_TIMEOUT = 100;
    private final BlockingQueue<Event> eventQueue;
    private final ObjectMapper objectMapper;
    private final DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService;

    public DocumentChangeWatcher(
            BlockingQueue<Event> eventQueue,
             ObjectMapper objectMapper,
             DocumentStorageServiceGrpc.DocumentStorageServiceStub documentStorageService
    ) {
        this.eventQueue = eventQueue;
        this.objectMapper = objectMapper;
        this.documentStorageService = documentStorageService;
    }

    @Override
    public void run() {
        var subscribeForDocumentChangesRequest = SubscribeForDocumentChangesRequest.newBuilder()
                .setBatchSize(BATCH_SIZE)
                .setBatchTimeout(BATCH_TIMEOUT)
                .build();
        documentStorageService.subscribeForDocumentsChanges(subscribeForDocumentChangesRequest, new StreamObserver<>() {
            @Override
            public void onNext(DocumentChangedEvents documentChangedEvents) {
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

            @Override
            public void onError(Throwable throwable) {
                LOGGER.warn("Error", throwable);
            }

            @Override
            public void onCompleted() {
                LOGGER.info("Stream completed");
            }
        });
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
