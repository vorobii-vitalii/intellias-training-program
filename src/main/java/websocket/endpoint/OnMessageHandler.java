package websocket.endpoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import serialization.Deserializer;
import tcp.server.CompositeInputStream;
import tcp.server.SocketConnection;
import websocket.domain.WebSocketMessage;

public class OnMessageHandler<T> implements BiConsumer<SocketConnection, WebSocketMessage> {
	private final Deserializer deserializer;
	private final Class<T> messageType;
	private final BiConsumer<SocketConnection, T> messageHandler;
	private final Consumer<IOException> onDeserializationError;

	public OnMessageHandler(
			Deserializer deserializer,
			Class<T> messageType,
			BiConsumer<SocketConnection, T> messageHandler,
			Consumer<IOException> onDeserializationError
	) {
		this.deserializer = deserializer;
		this.messageType = messageType;
		this.messageHandler = messageHandler;
		this.onDeserializationError = onDeserializationError;
	}

	@Override
	public void accept(SocketConnection socketConnection, WebSocketMessage message) {
		var payload = message.getPayload();
		if (message.isFin()) {
			var inputStream = new CompositeInputStream(socketConnection.getContextInputStream(), new ByteArrayInputStream(payload));
			try {
				var request = deserializer.deserialize(inputStream, messageType);
				messageHandler.accept(socketConnection, request);
				socketConnection.freeContext();
			}
			catch (IOException e) {
				onDeserializationError.accept(e);
			}
		} else {
			socketConnection.appendBytesToContext(payload);
		}
	}
}
