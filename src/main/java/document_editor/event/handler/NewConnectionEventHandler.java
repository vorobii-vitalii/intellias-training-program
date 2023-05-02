package document_editor.event.handler;

import static document_editor.PathUtils.toPairs;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import document_editor.DocumentStreamingWebSocketEndpoint;
import document_editor.PairDTO;
import document_editor.event.Event;
import document_editor.event.EventContext;
import document_editor.event.EventHandler;
import document_editor.event.EventType;
import document_editor.event.NewConnectionEvent;
import document_editor.mongo.MongoReactiveAtomBuffer;
import tcp.server.SocketConnection;
import util.Serializable;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class NewConnectionEventHandler implements EventHandler {
	private final MongoReactiveAtomBuffer mongoReactiveAtomBuffer;
	private final Supplier<Integer> connectionIdProvider;
	private final Executor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
	private final ObjectMapper objectMapper;

	public NewConnectionEventHandler(
			MongoReactiveAtomBuffer mongoReactiveAtomBuffer,
			Supplier<Integer> connectionIdProvider,
			ObjectMapper objectMapper
	) {
		this.mongoReactiveAtomBuffer = mongoReactiveAtomBuffer;
		this.connectionIdProvider = connectionIdProvider;
		this.objectMapper = objectMapper;
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
//			webSocketMessage.setPayload(new Gson()
//					.toJson(new DocumentStreamingWebSocketEndpoint.Response(
//							DocumentStreamingWebSocketEndpoint.ResponseType.ON_CONNECT,
//							new DocumentStreamingWebSocketEndpoint.ConnectDocumentReply(connectionIdProvider.get())))
//					.getBytes(StandardCharsets.UTF_8));
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
		executor.execute(() -> mongoReactiveAtomBuffer.streamDocument(path -> {
			var message = new WebSocketMessage();
			message.setFin(true);
//			message.setPayload(new Gson()
//					.toJson(new DocumentStreamingWebSocketEndpoint.Response(
//							DocumentStreamingWebSocketEndpoint.ResponseType.ADD, new PairDTO<>(toPairs(path.first()), path.second())))
//					.getBytes(StandardCharsets.UTF_8));
			try {
				message.setPayload(objectMapper.writeValueAsBytes(new DocumentStreamingWebSocketEndpoint.Response(
						DocumentStreamingWebSocketEndpoint.ResponseType.ADD, path)));
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
			message.setOpCode(OpCode.BINARY);
			var iterator = socketConnections.iterator();
			while (iterator.hasNext()) {
				var connection = iterator.next();
				try {
					sendMessage(connection, message);
					return true;
				} catch (Exception e) {
					iterator.remove();
				}
			}
			return !socketConnections.isEmpty();
		}));
	}
}
