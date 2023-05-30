package document_editor.event.handler.impl;

import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentElement;
import com.example.document.storage.DocumentElements;
import com.example.document.storage.DocumentStorageServiceGrpc;
import com.example.document.storage.FetchDocumentContentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.HttpServer;
import document_editor.dto.Change;
import document_editor.dto.ConnectDocumentReply;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.dto.TreePathDTO;
import document_editor.event.DocumentsEventType;
import document_editor.event.NewConnectionDocumentsEvent;
import document_editor.event.context.ClientConnectionsContext;
import document_editor.event.handler.EventHandler;
import grpc.ServiceDecorator;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import tcp.MessageSerializer;
import tcp.server.BufferCopier;
import tcp.server.SocketConnection;
import util.Serializable;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class NewConnectionEventHandler implements EventHandler<NewConnectionDocumentsEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewConnectionEventHandler.class);
    private final Supplier<Integer> connectionIdProvider;
    private final ObjectMapper objectMapper;
    private final DocumentStorageServiceGrpc.DocumentStorageServiceStub service;
    private final Tracer tracer;
    private final ServiceDecorator serviceDecorator;
    private final MessageSerializer messageSerializer;
    private final BufferCopier bufferCopier;

    public NewConnectionEventHandler(
            DocumentStorageServiceGrpc.DocumentStorageServiceStub service,
            Supplier<Integer> connectionIdProvider,
            ObjectMapper objectMapper,
            Tracer tracer,
            ServiceDecorator serviceDecorator,
            MessageSerializer messageSerializer,
            BufferCopier bufferCopier
    ) {
        this.service = service;
        this.connectionIdProvider = connectionIdProvider;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.serviceDecorator = serviceDecorator;
        this.messageSerializer = messageSerializer;
        this.bufferCopier = bufferCopier;
    }

    private void sendMessage(SocketConnection socketConnection, ByteBuffer buffer) {
        socketConnection.appendResponse(buffer, e -> {
        });
    }

    private void startWrite(Set<SocketConnection> socketConnections) {
        socketConnections.parallelStream()
                .forEach(connection -> {
                    try {
                        connection.changeOperation(OP_WRITE);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    @Override
    public DocumentsEventType<NewConnectionDocumentsEvent> getHandledEventType() {
        return DocumentsEventType.CONNECT;
    }

    private byte[] serialize(Object obj) throws IOException {
        var arrayOutputStream = new ByteArrayOutputStream();
        objectMapper.writeValue(new GZIPOutputStream(arrayOutputStream), obj);
        return arrayOutputStream.toByteArray();
    }

    @Override
    public void handle(Collection<NewConnectionDocumentsEvent> events, ClientConnectionsContext clientConnectionsContext) {
        Set<SocketConnection> socketConnections = Collections.synchronizedSet(new HashSet<>());
        sendConnectedAcknowledgements(events, clientConnectionsContext, socketConnections);
        var getDocumentSpan = tracer.spanBuilder("Get document").setSpanKind(SpanKind.CLIENT).startSpan();

        var scope = getDocumentSpan.makeCurrent();

        var fetchDocumentContentRequest = FetchDocumentContentRequest.newBuilder().setDocumentId(HttpServer.DOCUMENT_ID).build();
        var streamObserver = new StreamObserver<DocumentElements>() {
            @Override
            public void onNext(DocumentElements documentElements) {
                getDocumentSpan.addEvent("Batch received");
                distributeChange(documentElements);
            }

            private void distributeChange(DocumentElements documentElements) {
                var message = new WebSocketMessage();
                message.setFin(true);
                try {
                    message.setPayload(serialize(new Response(ResponseType.ADD_BULK, computeChanges(documentElements))));
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                message.setOpCode(OpCode.BINARY);
                getDocumentSpan.addEvent("Batch serialized");
                var buffer = messageSerializer.serialize(message, e -> {
                });
                socketConnections.parallelStream()
                        .forEach(connection -> {
                            try {
                                sendMessage(connection, bufferCopier.copy(buffer));
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                getDocumentSpan.addEvent("Batch sent");
            }

            private List<Change> computeChanges(DocumentElements documentElements) {
                return documentElements.getDocumentElementsList()
                        .stream()
                        .map(doc -> new Change(
                                toInternalPath(doc),
                                (char) doc.getCharacter()
                        ))
                        .collect(Collectors.toList());
            }

            private TreePathDTO toInternalPath(DocumentElement path) {
                return new TreePathDTO(path.getDirectionsList(), path.getDisambiguatorsList());
            }

            @Override
            public void onError(Throwable throwable) {
                getDocumentSpan.addEvent("Error occurred");
                getDocumentSpan.recordException(throwable);
                LOGGER.warn("Error when streaming document", throwable);
                getDocumentSpan.end();
                scope.close();
            }

            @Override
            public void onCompleted() {
                LOGGER.info("On complete streaming document");
                startWrite(socketConnections);
                getDocumentSpan.end();
                scope.close();
            }
        };
        serviceDecorator.decorateService(service).fetchDocumentContent(fetchDocumentContentRequest, streamObserver);
    }

    private void sendConnectedAcknowledgements(
            Collection<NewConnectionDocumentsEvent> events,
            ClientConnectionsContext clientConnectionsContext,
            Set<SocketConnection> socketConnections
    ) {
        for (var event : events) {
            var socketConnection = event.connection();
            var webSocketMessage = new WebSocketMessage();
            webSocketMessage.setFin(true);
            try {
                webSocketMessage.setPayload(serialize(new Response(
                        ResponseType.ON_CONNECT,
                        new ConnectDocumentReply(connectionIdProvider.get()))));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            webSocketMessage.setOpCode(OpCode.BINARY);
            try {
                sendMessage(socketConnection, messageSerializer.serialize(webSocketMessage, e -> {
                }));
            }
            catch (Exception e) {
                continue;
            }
            clientConnectionsContext.addOrUpdateConnection(socketConnection);
            socketConnections.add(socketConnection);
        }
    }
}
