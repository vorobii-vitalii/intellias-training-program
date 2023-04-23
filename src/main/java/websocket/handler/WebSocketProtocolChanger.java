package websocket.handler;

import http.protocol_change.ProtocolChangeContext;
import http.protocol_change.ProtocolChanger;
import util.Constants;
import websocket.endpoint.WebSocketEndpointProvider;

public class WebSocketProtocolChanger implements ProtocolChanger {
	private final WebSocketEndpointProvider webSocketEndpointProvider;

	public WebSocketProtocolChanger(WebSocketEndpointProvider webSocketEndpointProvider) {
		this.webSocketEndpointProvider = webSocketEndpointProvider;
	}

	@Override
	public void changeProtocol(ProtocolChangeContext protocolChangeContext) {
		var request = protocolChangeContext.request();
		var endpoint = request.getHttpRequestLine().path();
		var connection = protocolChangeContext.connection();
		connection.setProtocol(Constants.Protocol.WEB_SOCKET);
		connection.setMetadata(Constants.WebSocketMetadata.ENDPOINT, endpoint);
		webSocketEndpointProvider.getEndpoint(endpoint).onConnect(connection);
	}

	@Override
	public String getProtocolName() {
		return "websocket";
	}
}
