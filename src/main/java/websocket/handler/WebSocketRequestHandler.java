package websocket.handler;

import request_handler.NetworkRequest;
import request_handler.RequestHandler;
import util.Constants;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpointProvider;

public class WebSocketRequestHandler implements RequestHandler<WebSocketMessage> {
	private final WebSocketEndpointProvider webSocketEndpointProvider;

	public WebSocketRequestHandler(WebSocketEndpointProvider webSocketEndpointProvider) {
		this.webSocketEndpointProvider = webSocketEndpointProvider;
	}

	@Override
	public void handle(NetworkRequest<WebSocketMessage> networkRequest) {
		var socketConnection = networkRequest.socketConnection();
		var endpoint = socketConnection.getMetadata(Constants.WebSocketMetadata.ENDPOINT);
		webSocketEndpointProvider.getEndpoint(endpoint).onMessage(socketConnection, networkRequest.request());
	}
}
