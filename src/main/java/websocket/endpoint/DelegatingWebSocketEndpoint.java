package websocket.endpoint;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import tcp.server.SocketConnection;
import websocket.domain.OpCode;
import websocket.domain.WebSocketMessage;

public class DelegatingWebSocketEndpoint implements WebSocketEndpoint {
	private final Map<OpCode, BiConsumer<SocketConnection, WebSocketMessage>> messageHandlerByOpCode;
	private final Consumer<SocketConnection> onHandShakeCompletion;

	public DelegatingWebSocketEndpoint(
			Map<OpCode, BiConsumer<SocketConnection, WebSocketMessage>> messageHandlerByOpCode,
			Consumer<SocketConnection> onHandShakeCompletion
	) {
		this.messageHandlerByOpCode = messageHandlerByOpCode;
		this.onHandShakeCompletion = onHandShakeCompletion;
	}

	@Override
	public void onHandshakeCompletion(SocketConnection connection) {
		if (onHandShakeCompletion != null) {
			onHandShakeCompletion.accept(connection);
		}
	}

	@Override
	public void onMessage(SocketConnection connection, WebSocketMessage message) {
		var handler = messageHandlerByOpCode.get(message.getOpCode());
		if (handler != null) {
			handler.accept(connection, message);
		}
	}
}
