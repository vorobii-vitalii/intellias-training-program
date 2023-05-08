package http.handler;

import http.domain.HTTPRequest;
import http.domain.HTTPResponse;
import http.post_processor.ProtocolChangerHTTPResponsePostProcessor;
import http.protocol_change.ProtocolChangeContext;
import http.protocol_change.ProtocolChanger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import request_handler.NetworkRequest;
import tcp.server.ConnectionImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
	SelectionKey selectionKey;

	@Mock
	HTTPResponse httpResponse;

	@Mock
	SocketChannel socketChannel;

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
						.handle(new NetworkRequest<>(httpRequest, new ConnectionImpl(selectionKey), null), httpResponse);
		verify(protocolChanger1, never()).changeProtocol(any());
		verify(protocolChanger2, never()).changeProtocol(any());
	}

	@Test
	void handleGivenProtocolNotSupported() {
		when(httpResponse.isUpgradeResponse()).thenReturn(true);
		when(httpResponse.getUpgradeProtocol()).thenReturn("protocol-x");
		assertThrows(IllegalArgumentException.class, () ->
						protocolChangerHTTPResponsePostProcessor.handle(
										new NetworkRequest<>(httpRequest, new ConnectionImpl(selectionKey), null), httpResponse)
		);
		verify(protocolChanger1, never()).changeProtocol(any());
		verify(protocolChanger2, never()).changeProtocol(any());
	}

	@Test
	void handleGivenProtocolSupported() throws IOException {
		when(httpResponse.isUpgradeResponse()).thenReturn(true);
		when(httpResponse.getUpgradeProtocol()).thenReturn(PROTOCOL_1);
		when(selectionKey.channel()).thenReturn(socketChannel);
		when(socketChannel.getRemoteAddress()).thenReturn(new InetSocketAddress(123));
		protocolChangerHTTPResponsePostProcessor
						.handle(new NetworkRequest<>(httpRequest, new ConnectionImpl(selectionKey), null), httpResponse);
		verify(protocolChanger1)
						.changeProtocol(new ProtocolChangeContext(httpRequest, httpResponse, new ConnectionImpl(selectionKey)));
		verify(protocolChanger2, never()).changeProtocol(any());
	}

}
