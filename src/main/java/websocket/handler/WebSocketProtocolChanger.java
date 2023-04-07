package websocket.handler;

import http.handler.ProtocolChangeContext;
import http.handler.ProtocolChanger;
import tcp.server.ServerAttachment;
import util.Constants;
import websocket.endpoint.WebSocketEndpointProvider;

import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

public class WebSocketProtocolChanger implements ProtocolChanger {
	private final WebSocketEndpointProvider webSocketEndpointProvider;

	public WebSocketProtocolChanger(WebSocketEndpointProvider webSocketEndpointProvider) {
		this.webSocketEndpointProvider = webSocketEndpointProvider;
	}

	@Override
	public void changeProtocol(ProtocolChangeContext protocolChangeContext) {
		var selectionKey = protocolChangeContext.selectionKey();
		var request = protocolChangeContext.request();
		var attachmentObject = (ServerAttachment) (selectionKey.attachment());
		var endpoint = request.getHttpRequestLine().path();
		selectionKey.attach(new ServerAttachment(
						Constants.Protocol.WEB_SOCKET,
						attachmentObject.bufferContext(),
						new LinkedBlockingDeque<>(),
						Map.of(Constants.WebSocketMetadata.ENDPOINT, endpoint)
		));
		webSocketEndpointProvider.getEndpoint(endpoint).onConnect(selectionKey);
	}

	@Override
	public String getProtocolName() {
		return "websocket";
	}
}
