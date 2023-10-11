package websocket.handler;

import request_handler.NetworkRequest;
import request_handler.NetworkRequestHandler;
import util.Constants;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpointProvider;

public class WebSocketNetworkRequestHandler implements NetworkRequestHandler<WebSocketMessage> {
	private final WebSocketEndpointProvider webSocketEndpointProvider;

	public WebSocketNetworkRequestHandler(WebSocketEndpointProvider webSocketEndpointProvider) {
		this.webSocketEndpointProvider = webSocketEndpointProvider;
	}

	@Override
	public void handle(NetworkRequest<WebSocketMessage> networkRequest) {
		var socketConnection = networkRequest.socketConnection();
		var endpoint = socketConnection.getMetadata(Constants.WebSocketMetadata.ENDPOINT);
		webSocketEndpointProvider.getEndpoint(endpoint).onMessage(socketConnection, networkRequest.request());
	}
}