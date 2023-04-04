package websocket.endpoint;

import tcp.server.ServerAttachment;
import websocket.OpCode;
import websocket.WebSocketMessage;

import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;

public class WebSockerEndpointExample implements WebSocketEndpoint {
	@Override
	public void onConnect(SelectionKey selectionKey) {

	}

	@Override
	public void onMessage(SelectionKey selectionKey, WebSocketMessage message) {

	}
}
