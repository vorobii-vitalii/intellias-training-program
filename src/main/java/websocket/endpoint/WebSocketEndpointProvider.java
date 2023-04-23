package websocket.endpoint;

import java.util.Map;

public class WebSocketEndpointProvider {
	private final Map<String, WebSocketEndpoint> endpointMap;

	public WebSocketEndpointProvider(Map<String, WebSocketEndpoint> endpointMap) {
		this.endpointMap = endpointMap;
	}

	public WebSocketEndpoint getEndpoint(String endpointUrl) {
		return endpointMap.get(endpointUrl);
	}
}
