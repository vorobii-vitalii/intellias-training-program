package websocket.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import request_handler.NetworkRequest;
import tcp.server.SocketConnection;
import util.Constants;
import websocket.domain.WebSocketMessage;
import websocket.endpoint.WebSocketEndpoint;
import websocket.endpoint.WebSocketEndpointProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketNetworkClientSIPRequestHandlerTest {

	public static final String ENDPOINT = "/endpoint";
	@Mock
	WebSocketEndpointProvider webSocketEndpointProvider;

	@InjectMocks
    WebSocketNetworkRequestHandler webSocketRequestHandler;

	@Test
	void handle() {
		var webSocketMessage = new WebSocketMessage();
		var socketConnection = mock(SocketConnection.class);
		when(socketConnection.getMetadata(Constants.WebSocketMetadata.ENDPOINT)).thenReturn(ENDPOINT);
		var networkRequest = new NetworkRequest<>(webSocketMessage, socketConnection);
		var webSocketEndpoint = mock(WebSocketEndpoint.class);
		when(webSocketEndpointProvider.getEndpoint(ENDPOINT)).thenReturn(webSocketEndpoint);
		webSocketRequestHandler.handle(networkRequest);
		verify(webSocketEndpoint).onMessage(socketConnection, webSocketMessage);
	}
}