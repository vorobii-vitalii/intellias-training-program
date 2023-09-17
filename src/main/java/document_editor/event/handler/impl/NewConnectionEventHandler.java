package document_editor.event.handler.impl;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentElements;
import com.example.document.storage.FetchDocumentContentRequest;
import com.example.document.storage.RxDocumentStorageServiceGrpc;

import document_editor.HttpServer;
import document_editor.dto.Change;
import document_editor.dto.Changes;
import document_editor.dto.ConnectDocumentReply;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.event.DocumentsEventType;
import document_editor.event.NewConnectionDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import grpc.ServiceDecorator;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.reactivex.Single;
import reactor.core.CoreSubscriber;
import serialization.Serializer;
import tcp.MessageSerializerImpl;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class NewConnectionEventHandler implements EventHandler<NewConnectionDocumentsEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewConnectionEventHandler.class);
    private final Supplier<Integer> connectionIdProvider;
    private final RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub service;
    private final Tracer tracer;
    private final ServiceDecorator serviceDecorator;
    private final MessageSerializerImpl messageSerializer;
    private final Serializer serializer;

    public NewConnectionEventHandler(
            RxDocumentStorageServiceGrpc.RxDocumentStorageServiceStub service,
            Supplier<Integer> connectionIdProvider,
            Tracer tracer,
            ServiceDecorator serviceDecorator,
            MessageSerializerImpl messageSerializer,
            Serializer serializer
    ) {
        this.service = service;
        this.connectionIdProvider = connectionIdProvider;
        this.tracer = tracer;
        this.serviceDecorator = serviceDecorator;
        this.messageSerializer = messageSerializer;
        this.serializer = serializer;
    }

    @Override
    public DocumentsEventType<NewConnectionDocumentsEvent> getHandledEventType() {
        return DocumentsEventType.CONNECT;
    }

    @Override
    public void handle(NewConnectionDocumentsEvent event, ClientConnectionsContext clientConnectionsContext) {
        var connection = event.connection();
        var webSocketMessage = new WebSocketMessage();
        webSocketMessage.setFin(true);
        try {
            webSocketMessage.setPayload(serializer.serialize(new Response(
                    ResponseType.ON_CONNECT,
                    new ConnectDocumentReply(connectionIdProvider.get()))));
        }
        catch (IOException e) {
            LOGGER.error("Serialization error", e);
            return;
        }
        webSocketMessage.setOpCode(OpCode.BINARY);
        try {
            connection.appendResponse(messageSerializer.serialize(webSocketMessage));
            connection.changeOperation(OperationType.WRITE);
        }
        catch (Exception e) {
            LOGGER.error("Serialization error", e);
            return;
        }
        LOGGER.info("Adding connection {} to context", connection);
        clientConnectionsContext.addOrUpdateConnection(connection);
        startDocumentStreamingToClient(event, connection);
    }

    private void startDocumentStreamingToClient(NewConnectionDocumentsEvent event, SocketConnection connection) {
        var getDocumentSpan = tracer.spanBuilder("Get document")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(Context.current().with(connection.getSpan()))
                .startSpan();
        var scope = getDocumentSpan.makeCurrent();

        var fetchDocumentContentRequest = FetchDocumentContentRequest.newBuilder()
                .setDocumentId(HttpServer.DOCUMENT_ID)
                .setBatchSize(event.batchSize())
                .build();

        Single.just(fetchDocumentContentRequest)
                .flatMapPublisher(c -> serviceDecorator.decorateService(service).fetchDocumentContent(c))
                        .subscribe(new CoreSubscriber<>() {

                            private Subscription subscription;

                            @Override
                            public void onSubscribe(Subscription subscription) {
                                this.subscription = subscription;
                                subscription.request(1);
                            }

                            @Override
                            public void onNext(DocumentElements documentElements) {
                                sendChanges(computeChanges(documentElements), false, c -> subscription.request(1));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                getDocumentSpan.addEvent("Error occurred");
                                getDocumentSpan.recordException(throwable);
                                LOGGER.warn("Error when streaming document", throwable);
                                // TODO: Send error to client
                            }

                            @Override
                            public void onComplete() {
                                LOGGER.info("On complete streaming document");
                                sendChanges(List.of(), true, s -> {
                                });
                                getDocumentSpan.end();
                                scope.close();
                            }

                            private List<Change> computeChanges(DocumentElements documentElements) {
                                return documentElements.getDocumentElementsList()
                                        .stream()
                                        .map(doc -> new Change(
                                                doc.getCharId(),
                                                doc.getParentCharId(),
                                                doc.getIsRight(),
                                                doc.getDisambiguator(),
                                                doc.hasCharacter() ? ((char) doc.getCharacter()) : null
                                        ))
                                        .collect(Collectors.toList());
                            }

                            private void sendChanges(
                                    List<Change> changes,
                                    boolean isEnd,
                                    Consumer<SocketConnection> onChangeWrite
                            ) {
                                getDocumentSpan.addEvent("Batch received");
                                var message = new WebSocketMessage();
                                message.setFin(true);
                                try {
                                    message.setPayload(serializer.serialize(new Response(
                                            ResponseType.CHANGES,
                                            new Changes(changes, isEnd, "snapshot")
                                    )));
                                }
                                catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                message.setOpCode(OpCode.BINARY);
                                getDocumentSpan.addEvent("Batch serialized");
                                var buffer = messageSerializer.serialize(message, e -> {
                                });
                                connection.appendResponse(buffer, connection1 -> {
                                    getDocumentSpan.addEvent("Executing callback");
                                    onChangeWrite.accept(connection1);
                                    getDocumentSpan.addEvent("Executed callback");
                                });
                                getDocumentSpan.addEvent("Batch sent");
                            }
                        });
    }

}
