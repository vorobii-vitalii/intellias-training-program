package websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import http.domain.HTTPHeaders;
import http.domain.HTTPMethod;
import http.domain.HTTPRequest;
import http.domain.HTTPRequestLine;
import http.domain.HTTPVersion;
import util.Constants;
import websocket.endpoint.WebSocketEndpoint;
import websocket.endpoint.WebSocketEndpointProvider;

@ExtendWith(MockitoExtension.class)
class TestWebSocketChangeProtocolHTTPHandlerStrategy {
	private static final String KEY = "keyaeignSIOghNSh";
	private static final String ENDPOINT_PATH = "/path";
	private static final HTTPRequest HTTP_REQUEST = new HTTPRequest(
			new HTTPRequestLine(HTTPMethod.GET, ENDPOINT_PATH, new HTTPVersion(1, 1)),
			new HTTPHeaders()
					.addSingleHeader(Constants.HTTPHeaders.UPGRADE, "websocket")
					.addSingleHeader(Constants.HTTPHeaders.CONNECTION, "Upgrade")
					.addSingleHeader(Constants.HTTPHeaders.WEBSOCKET_KEY, KEY),
			new byte[] {}
	);
	private static final String EXPECTED_WEBSOCKET_ACCEPT = Base64.getEncoder()
			.encodeToString(DigestUtils.sha1(KEY + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"));

	@Mock
	private Predicate<HTTPRequest> predicate;

	@Mock
	WebSocketEndpointProvider webSocketEndpointProvider;

	@Mock
	WebSocketEndpoint webSocketEndpoint;

	WebSocketChangeProtocolHTTPHandlerStrategy httpHandlerStrategy;

	@BeforeEach
	void init() {
		httpHandlerStrategy = new WebSocketChangeProtocolHTTPHandlerStrategy(List.of(predicate), webSocketEndpointProvider);
	}

	@Test
	void supportsGivenRequestNotMatch() {
		when(predicate.test(HTTP_REQUEST)).thenReturn(false);
		assertThat(httpHandlerStrategy.supports(HTTP_REQUEST)).isFalse();
	}

	@Test
	void supportsGivenRequestMatch() {
		when(predicate.test(HTTP_REQUEST)).thenReturn(true);
		assertThat(httpHandlerStrategy.supports(HTTP_REQUEST)).isTrue();
	}

	@Test
	void handleRequestGivenEndpointMappingNotFound() {
		when(webSocketEndpointProvider.getEndpoint(ENDPOINT_PATH)).thenReturn(null);
		var httpResponse = httpHandlerStrategy.handleRequest(HTTP_REQUEST);
		assertThat(httpResponse.responseLine().statusCode()).isEqualTo(Constants.HTTPStatusCode.NOT_FOUND);
	}

	@Test
	void handleRequestGivenEndpointMappingFound() {
		when(webSocketEndpointProvider.getEndpoint(ENDPOINT_PATH)).thenReturn(webSocketEndpoint);
		var httpResponse = httpHandlerStrategy.handleRequest(HTTP_REQUEST);
		assertThat(httpResponse.responseLine().statusCode()).isEqualTo(Constants.HTTPStatusCode.SWITCHING_PROTOCOL);
		assertThat(httpResponse.httpHeaders().getHeaderValue(Constants.HTTPHeaders.WEBSOCKET_ACCEPT))
				.contains(EXPECTED_WEBSOCKET_ACCEPT);
	}
}