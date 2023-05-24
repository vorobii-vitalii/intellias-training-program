package websocket.endpoint;

import tcp.server.SocketConnection;
import websocket.domain.WebSocketMessage;

public interface WebSocketEndpoint {
	void onHandshakeCompletion(SocketConnection connection);
	void onMessage(SocketConnection connection, WebSocketMessage message);
}
