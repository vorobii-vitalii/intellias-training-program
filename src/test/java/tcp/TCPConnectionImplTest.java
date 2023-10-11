package tcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcp.client.TCPConnectionImpl;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TCPConnectionImplTest {
	@Mock
	private SocketChannel socketChannel;

	@InjectMocks
	private TCPConnectionImpl tcpConnection;

	@Test
	void close() throws IOException {
		tcpConnection.close();
		verify(socketChannel).close();
	}

}