package http.handler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import http.post_processor.ProtocolChangerHTTPResponsePostProcessor;
import http.protocol_change.ProtocolChangeContext;
import http.protocol_change.ProtocolChanger;
import request_handler.NetworkRequest;
import tcp.server.SocketConnection;

@ExtendWith(MockitoExtension.class)
class ProtocolChangerHTTPResponsePostProcessorTest {
	private static final String PROTOCOL_1 = "protocol1";
	private static final String PROTOCOL_2 = "protocol2";

	@Mock
	ProtocolChanger protocolChanger1;

	@Mock
	ProtocolChanger protocolChanger2;

	@Mock
	HTTPRequest httpRequest;

	@Mock
	HTTPResponse httpResponse;

	@Mock
	SocketConnection socketConnection;

	ProtocolChangerHTTPResponsePostProcessor protocolChangerHTTPResponsePostProcessor;

	@BeforeEach
	void init() {
		when(protocolChanger1.getProtocolName()).thenReturn(PROTOCOL_1);
		when(protocolChanger2.getProtocolName()).thenReturn(PROTOCOL_2);
		protocolChangerHTTPResponsePostProcessor = new ProtocolChangerHTTPResponsePostProcessor(List.of(
						protocolChanger1,
						protocolChanger2
		));
	}


	@Test
	void handleGivenNotUpgradeResponse() {
		when(httpResponse.isUpgradeResponse()).thenReturn(false);
		protocolChangerHTTPResponsePostProcessor
						.handle(new NetworkRequest<>(httpRequest, socketConnection), httpResponse);
		verify(protocolChanger1, never()).changeProtocol(any());
		verify(protocolChanger2, never()).changeProtocol(any());
	}

	@Test
	void handleGivenProtocolNotSupported() {
		when(httpResponse.isUpgradeResponse()).thenReturn(true);
		when(httpResponse.getUpgradeProtocol()).thenReturn("protocol-x");
		assertThrows(IllegalArgumentException.class, () ->
						protocolChangerHTTPResponsePostProcessor.handle(
										new NetworkRequest<>(httpRequest, socketConnection), httpResponse));
		verify(protocolChanger1, never()).changeProtocol(any());
		verify(protocolChanger2, never()).changeProtocol(any());
	}

	@Test
	void handleGivenProtocolSupported() {
		when(httpResponse.isUpgradeResponse()).thenReturn(true);
		when(httpResponse.getUpgradeProtocol()).thenReturn(PROTOCOL_1);
		protocolChangerHTTPResponsePostProcessor
						.handle(new NetworkRequest<>(httpRequest, socketConnection), httpResponse);
		verify(protocolChanger1)
						.changeProtocol(new ProtocolChangeContext(httpRequest, httpResponse, socketConnection));
		verify(protocolChanger2, never()).changeProtocol(any());
	}

}
