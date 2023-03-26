package echo;

import echo.connection.EchoConnectionImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcp.client.TCPConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EchoConnectionImplTest {

	@Mock
	private TCPConnection tcpConnection;

	@Mock
	private SocketChannel socketChannel;

	@InjectMocks
	private EchoConnectionImpl echoConnection;

	@Test
	void sendMessage() throws IOException {
		var message = "Message";
		when(tcpConnection.socketChannel()).thenReturn(socketChannel);
		when(socketChannel.write(any(ByteBuffer.class)))
						.thenAnswer(invocation -> {
							ByteBuffer buffer = invocation.getArgument(0);
							buffer.position(buffer.capacity());
							return buffer.capacity();
						});
		when(socketChannel.read(any(ByteBuffer.class)))
						.thenAnswer(invocation -> {
							ByteBuffer buffer = invocation.getArgument(0);
							buffer.put(message.getBytes(StandardCharsets.UTF_8));
							return buffer.capacity();
						});
		var receivedMessage = echoConnection.sendMessage(message);
		assertThat(receivedMessage).isEqualTo(message);
	}

	@Test
	void close() throws Exception {
		echoConnection.close();
		verify(tcpConnection).close();
	}

}