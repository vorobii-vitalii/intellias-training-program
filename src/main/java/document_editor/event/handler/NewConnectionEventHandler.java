package document_editor.event.handler;

import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.document.storage.DocumentElement;
import com.example.document.storage.DocumentStorageServiceGrpc;
import com.example.document.storage.FetchDocumentContentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import document_editor.HttpServer;
import document_editor.dto.Change;
import document_editor.dto.ConnectDocumentReply;
import document_editor.dto.Response;
import document_editor.dto.ResponseType;
import document_editor.dto.TreePathDTO;
import document_editor.event.Event;
import document_editor.event.EventType;
import document_editor.event.NewConnectionEvent;
import document_editor.event.context.EventContext;
import grpc.TracingContextPropagator;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import tcp.server.SocketConnection;
import util.Serializable;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class NewConnectionEventHandler implements EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewConnectionEventHandler.class);
    private final Supplier<Integer> connectionIdProvider;
    private final ObjectMapper objectMapper;
    private final Timer timer;
    private final DocumentStorageServiceGrpc.DocumentStorageServiceStub service;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public NewConnectionEventHandler(
            DocumentStorageServiceGrpc.DocumentStorageServiceStub service,
            Supplier<Integer> connectionIdProvider,
            ObjectMapper objectMapper,
            Timer timer,
            OpenTelemetry openTelemetry
    ) {
        this.service = service;
        this.connectionIdProvider = connectionIdProvider;
        this.objectMapper = objectMapper;
        this.timer = timer;
        this.openTelemetry = openTelemetry;
        tracer = openTelemetry.getTracer(NewConnectionEventHandler.class.getSimpleName());
    }

    private void sendMessage(SocketConnection socketConnection, Serializable serializable) {
        socketConnection.changeOperation(OP_WRITE);
        socketConnection.appendResponse(serializable);
        socketConnection.changeOperation(OP_WRITE);
    }

    @Override
    public EventType getHandledEventType() {
        return EventType.CONNECT;
    }

    private byte[] serialize(Object obj) throws IOException {
        var arrayOutputStream = new ByteArrayOutputStream();
        objectMapper.writeValue(new GZIPOutputStream(arrayOutputStream), obj);
        return arrayOutputStream.toByteArray();
    }

    @Override
    public void handle(Collection<Event> events, EventContext eventContext) {
        Set<SocketConnection> socketConnections = new HashSet<>();
        for (var event : events) {
            var newConnectionEvent = (NewConnectionEvent) event;
            var socketConnection = newConnectionEvent.connection();
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
                sendMessage(socketConnection, webSocketMessage);
            }
            catch (Exception e) {
                continue;
            }
            eventContext.addOrUpdateConnection(socketConnection);
            socketConnections.add(socketConnection);
        }

        var getDocumentSpan = tracer.spanBuilder("Get document")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(socketConnections.stream()
                        .reduce(Context.current(), (ctx, conn) -> ctx.with(conn.getConnectionSpan()), (a, b) -> a))
                .startSpan();

        var scope = getDocumentSpan.makeCurrent();

        service.withCallCredentials(new TracingContextPropagator(Context.current(), openTelemetry))
                .fetchDocumentContent(
                        FetchDocumentContentRequest.newBuilder().setDocumentId(HttpServer.DOCUMENT_ID).build(),
                        new StreamObserver<>() {
                            @Override
                            public void onNext(com.example.document.storage.DocumentElements documentElements) {
                                getDocumentSpan.addEvent("Batch received");
                                var message = new WebSocketMessage();
                                message.setFin(true);
                                try {
                                    var changes = documentElements.getDocumentElementsList()
                                            .stream()
                                            .map(doc -> new Change(
                                                    toInternalPath(doc),
                                                    (char) doc.getCharacter()
                                            ))
                                            .collect(Collectors.toList());
                                    message.setPayload(serialize(new Response(ResponseType.ADD_BULK, changes)));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                getDocumentSpan.addEvent("Batch serialized");
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
                                getDocumentSpan.addEvent("Batch sent");
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
                                getDocumentSpan.end();
                                scope.close();
                            }
                        });
    }
}
