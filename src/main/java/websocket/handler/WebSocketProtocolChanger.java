package websocket.handler;

import java.util.Set;

import http.protocol_change.ProtocolChangeContext;
import http.protocol_change.ProtocolChanger;
import util.Constants;
import websocket.endpoint.WebSocketEndpointProvider;

public class WebSocketProtocolChanger implements ProtocolChanger {
	private final WebSocketEndpointProvider webSocketEndpointProvider;
	private final Set<String> supportedProtocols;

	public WebSocketProtocolChanger(WebSocketEndpointProvider webSocketEndpointProvider) {
		this(webSocketEndpointProvider, Set.of());
	}

	public WebSocketProtocolChanger(WebSocketEndpointProvider webSocketEndpointProvider, Set<String> supportedProtocols) {
		this.webSocketEndpointProvider = webSocketEndpointProvider;
		this.supportedProtocols = supportedProtocols;
	}

	@Override
	public void changeProtocol(ProtocolChangeContext protocolChangeContext) {
		var request = protocolChangeContext.request();
		var endpoint = request.getHttpRequestLine().path();
		var connection = protocolChangeContext.connection();
		connection.setProtocol(Constants.Protocol.WEB_SOCKET);
		connection.setMetadata(Constants.WebSocketMetadata.ENDPOINT, endpoint);

		request.getSupportedProtocols().stream()
				.filter(supportedProtocols::contains)
				.findFirst()
				.ifPresent(protocol -> connection.setMetadata(Constants.WebSocketMetadata.SUB_PROTOCOL, protocol));
		webSocketEndpointProvider.getEndpoint(endpoint).onHandshakeCompletion(connection);
	}

	@Override
	public String getProtocolName() {
		return "websocket";
	}
}
