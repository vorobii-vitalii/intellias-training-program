import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import server.TCPClient;
import server.TCPClientConfig;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestTCPClient {
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

	TCPClient tcpClient;

	@Mock
	SelectorProvider selectorProvider;

	@Mock
	SocketChannel socketChannel;

	@BeforeEach
	void init() throws IOException {
		tcpClient = new TCPClient(CLIENT_CONFIG, selectorProvider);
		when(selectorProvider.openSocketChannel(PROTOCOL_FAMILY)).thenReturn(socketChannel);
	}

	@Test
	void testConnect() throws IOException {
		tcpClient.connect();
		verify(socketChannel).connect(new InetSocketAddress(HOST, PORT));
	}

	@Test
	void testClose() throws IOException {
		tcpClient.connect();
		tcpClient.close();
		verify(socketChannel).close();
	}

	@Test
	void testWriteGivenConnectionNotEstablished() {
		assertThrows(IllegalStateException.class, () -> tcpClient.write(ByteBuffer.allocate(BUFFER_SIZE)));
	}

	@Test
	void testWrite() throws IOException {
		tcpClient.connect();
		var byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		when(socketChannel.write(byteBuffer))
				.thenAnswer(invocation -> byteBuffer.position(BUFFER_SIZE / 2).position())
				.thenAnswer(invocation -> byteBuffer.position(BUFFER_SIZE).position());
		tcpClient.write(byteBuffer);
		assertThat(byteBuffer.position()).isEqualTo(BUFFER_SIZE);
	}

	@Test
	void testReadGivenConnectionNotEstablished() {
		assertThrows(IllegalStateException.class, () -> tcpClient.read(buffer -> true));
	}

	@Test
	void testRead() throws IOException {
		tcpClient.connect();
		when(socketChannel.read(any(ByteBuffer.class)))
				.thenAnswer(getReadInvocationHandler((byte) 1))
				.thenAnswer(getReadInvocationHandler((byte) 2))
				.thenAnswer(getReadInvocationHandler((byte) 3))
				.thenAnswer(getReadInvocationHandler((byte) 4))
				.thenAnswer(getReadInvocationHandler((byte) 5));
		tcpClient.read(buffer -> buffer.get() != (byte) 5);
		verify(socketChannel, times(5)).read(any(ByteBuffer.class));
	}

	private static Answer<Object> getReadInvocationHandler(byte num) {
		return invocation -> {
			ByteBuffer buffer = invocation.getArgument(0);
			buffer.put(num);
			return 1;
		};
	}

}
