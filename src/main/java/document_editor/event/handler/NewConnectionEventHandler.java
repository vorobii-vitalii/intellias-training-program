package document_editor.event.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import document_editor.DocumentStreamingWebSocketEndpoint;
import document_editor.HttpServer;
import document_editor.event.*;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tcp.server.SocketConnection;
import util.Serializable;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.channels.SelectionKey.OP_WRITE;

public class NewConnectionEventHandler implements EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewConnectionEventHandler.class);
    private final Supplier<Integer> connectionIdProvider;
    private final ObjectMapper objectMapper;
    private final Timer timer;
    private final com.example.document.storage.DocumentStorageServiceGrpc.DocumentStorageServiceStub service;

    public NewConnectionEventHandler(
            com.example.document.storage.DocumentStorageServiceGrpc.DocumentStorageServiceStub service,
            Supplier<Integer> connectionIdProvider,
            ObjectMapper objectMapper,
            Timer timer
    ) {
        this.service = service;
        this.connectionIdProvider = connectionIdProvider;
        this.objectMapper = objectMapper;
        this.timer = timer;
    }

    private void sendMessage(SocketConnection socketConnection, Serializable serializable) {
        socketConnection.appendResponse(serializable);
        socketConnection.changeOperation(OP_WRITE);
    }

    @Override
    public EventType getHandledEventType() {
        return EventType.CONNECT;
    }

    @Override
    public void handle(Collection<Event> events, EventContext eventContext) {
        Set<SocketConnection> socketConnections = new HashSet<>();
        for (Event event : events) {
            var newConnectionEvent = (NewConnectionEvent) event;
            var socketConnection = newConnectionEvent.connection();
            var webSocketMessage = new WebSocketMessage();
            webSocketMessage.setFin(true);
            try {
                webSocketMessage.setPayload(objectMapper.writeValueAsBytes(new DocumentStreamingWebSocketEndpoint.Response(
                        DocumentStreamingWebSocketEndpoint.ResponseType.ON_CONNECT,
                        new DocumentStreamingWebSocketEndpoint.ConnectDocumentReply(connectionIdProvider.get()))));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            webSocketMessage.setOpCode(OpCode.BINARY);
            try {
                sendMessage(socketConnection, webSocketMessage);
            } catch (Exception e) {
                return;
            }
            eventContext.addConnection(socketConnection);
            socketConnections.add(socketConnection);
        }
        service.fetchDocumentContent(
                com.example.document.storage.FetchDocumentContentRequest.newBuilder().setDocumentId(HttpServer.DOCUMENT_ID).build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(com.example.document.storage.DocumentElements documentElements) {
                        var message = new WebSocketMessage();
                        message.setFin(true);
                        try {
                            var changes = documentElements.getDocumentElementsList()
                                    .stream()
                                    .map(doc -> new DocumentStreamingWebSocketEndpoint.Change(
                                            toInternalPath(doc.getPath()),
                                            (char) doc.getCharacter()
                                    ))
                                    .collect(Collectors.toList());
                            message.setPayload(objectMapper.writeValueAsBytes(new DocumentStreamingWebSocketEndpoint.Response(
                                    DocumentStreamingWebSocketEndpoint.ResponseType.ADD_BULK, changes)));
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        message.setOpCode(OpCode.BINARY);
                        var iterator = socketConnections.iterator();
                        while (iterator.hasNext()) {
                            var connection = iterator.next();
                            try {
                                sendMessage(connection, message);
                            } catch (Exception e) {
                                iterator.remove();
                            }
                        }
                    }

                    private List<DocumentStreamingWebSocketEndpoint.TreePathEntry> toInternalPath(com.example.document.storage.TreePath path) {
                        return path.getEntriesList().stream()
                                .map(entry -> new DocumentStreamingWebSocketEndpoint.TreePathEntry(
                                        entry.getIsLeft(),
                                        entry.getDisambiguator()
                                ))
                                .collect(Collectors.toList());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        LOGGER.warn("Error when streaming document", throwable);
                    }

                    @Override
                    public void onCompleted() {
                        LOGGER.info("On complete streaming document");
                    }
                });
    }
}
