package websocket.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestWebSocketEndpointProvider {
	private static final String ENDPOINT_1 = "/documents";
	private static final String ENDPOINT_2 = "/documents2";

	@Mock
	WebSocketEndpoint webSocketEndpoint1;

	@Mock
	WebSocketEndpoint webSocketEndpoint2;

	WebSocketEndpointProvider webSocketEndpointProvider;

	@BeforeEach
	void init() {
		webSocketEndpointProvider = new WebSocketEndpointProvider(
				Map.of(
						ENDPOINT_1, webSocketEndpoint1,
						ENDPOINT_2, webSocketEndpoint2
				));
	}

	@Test
	void getEndpoint() {
		assertThat(webSocketEndpointProvider.getEndpoint(ENDPOINT_1)).isEqualTo(webSocketEndpoint1);
		assertThat(webSocketEndpointProvider.getEndpoint(ENDPOINT_2)).isEqualTo(webSocketEndpoint2);
		assertThat(webSocketEndpointProvider.getEndpoint("eA#tQ#tQ#t")).isNull();
	}
}
