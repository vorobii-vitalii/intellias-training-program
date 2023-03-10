import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tcp.TCPClientImpl;
import tcp.TCPClientConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestTCPClientImpl {
	private static final String HOST = "81.243.245.1";
	private static final int PORT = 8912;
	private static final int BUFFER_SIZE = 100;
	private static final StandardProtocolFamily PROTOCOL_FAMILY = StandardProtocolFamily.INET;
	private static final TCPClientConfig CLIENT_CONFIG = TCPClientConfig.builder()
			.setHost(HOST)
			.setPort(PORT)
			.setBufferSize(BUFFER_SIZE)
			.setProtocolFamily(PROTOCOL_FAMILY)
			.build();

	TCPClientImpl tcpClient;

	@Mock
	SelectorProvider selectorProvider;

	@Mock
	SocketChannel socketChannel;

	@BeforeEach
	void init() {
		tcpClient = new TCPClientImpl(CLIENT_CONFIG, selectorProvider);
	}

	@Test
	void testConnect() throws IOException {
		when(selectorProvider.openSocketChannel(PROTOCOL_FAMILY)).thenReturn(socketChannel);
		var connection = tcpClient.createConnection();
		assertThat(connection).isNotNull();
		verify(socketChannel).connect(new InetSocketAddress(HOST, PORT));
	}

}
