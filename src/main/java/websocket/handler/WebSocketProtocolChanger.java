package websocket.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import http.protocol_change.ProtocolChangeContext;
import http.protocol_change.ProtocolChanger;
import util.Constants;
import websocket.endpoint.WebSocketEndpointProvider;

public class WebSocketProtocolChanger implements ProtocolChanger {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketProtocolChanger.class);

	private final WebSocketEndpointProvider webSocketEndpointProvider;

	public WebSocketProtocolChanger(WebSocketEndpointProvider webSocketEndpointProvider) {
		this.webSocketEndpointProvider = webSocketEndpointProvider;
	}

	@Override
	public void changeProtocol(ProtocolChangeContext protocolChangeContext) {
		var request = protocolChangeContext.request();
		var endpoint = request.getHttpRequestLine().path();
		var connection = protocolChangeContext.connection();
//		LOGGER.info("Changing protocol of {} to websocket, endpoint = {}", connection, endpoint);
		connection.setProtocol(Constants.Protocol.WEB_SOCKET);
		connection.setMetadata(Constants.WebSocketMetadata.ENDPOINT, endpoint);
		webSocketEndpointProvider.getEndpoint(endpoint).onHandshakeCompletion(connection);
	}

	@Override
	public String getProtocolName() {
		return "websocket";
	}
}
