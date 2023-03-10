package echo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcp.TCPConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EchoConnectionImplTest {

	@Mock
	private TCPConnection tcpConnection;

	@InjectMocks
	private EchoConnectionImpl echoConnection;

	@Test
	void sendMessage() throws IOException {
		var message = "Message";
		doAnswer(invocation -> {
			Function<ByteBuffer, Boolean> function = invocation.getArgument(0);
			for (var c : message.toCharArray()) {
				function.apply(ByteBuffer.wrap(new byte[]{(byte) c}));
			}
			return null;
		}).when(tcpConnection).read(any());
		var receivedMessage = echoConnection.sendMessage(message);
		assertThat(receivedMessage).isEqualTo(message);
		verify(tcpConnection).write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void close() throws Exception {
		echoConnection.close();
		verify(tcpConnection).close();
	}

}