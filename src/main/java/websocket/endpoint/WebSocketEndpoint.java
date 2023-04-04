package websocket.endpoint;

import websocket.WebSocketMessage;

import java.nio.channels.SelectionKey;

public interface WebSocketEndpoint {
	void onConnect(SelectionKey selectionKey);
	void onMessage(SelectionKey selectionKey, WebSocketMessage message);
}
