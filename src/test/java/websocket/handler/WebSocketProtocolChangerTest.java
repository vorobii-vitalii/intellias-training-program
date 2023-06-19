package websocket.handler;

import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import http.domain.HTTPVersion;
import http.protocol_change.ProtocolChangeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcp.server.SocketConnection;
import util.Constants;
import websocket.endpoint.WebSocketEndpoint;
import websocket.endpoint.WebSocketEndpointProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketProtocolChangerTest {
	private static final String ENDPOINT = "/path";

	@Mock
	WebSocketEndpointProvider webSocketEndpointProvider;

	@InjectMocks
	WebSocketProtocolChanger webSocketProtocolChanger;

	@Test
	void changeProtocol() {
		var socketConnection = mock(SocketConnection.class);
		var webSocketEndpoint = mock(WebSocketEndpoint.class);
		var httpRequest = new HTTPRequest(new HTTPRequestLine(
						HTTPMethod.GET,
						ENDPOINT,
						new HTTPVersion(1, 1)
		));
		var protocolChangeContext = new ProtocolChangeContext(httpRequest, null, socketConnection);
		when(webSocketEndpointProvider.getEndpoint(ENDPOINT)).thenReturn(webSocketEndpoint);
		webSocketProtocolChanger.changeProtocol(protocolChangeContext);
		verify(webSocketEndpoint).onHandshakeCompletion(socketConnection);
		verify(socketConnection).setProtocol(Constants.Protocol.WEB_SOCKET);
		verify(socketConnection).setMetadata(Constants.WebSocketMetadata.ENDPOINT, ENDPOINT);
	}

	@Test
	void getProtocolName() {
		assertThat(webSocketProtocolChanger.getProtocolName()).isEqualTo("websocket");
	}
}