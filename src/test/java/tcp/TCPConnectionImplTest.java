package tcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TCPConnectionImplTest {
	private static final int CAPACITY = 100;

	@Mock
	private SocketChannel socketChannel;

	private final ByteBuffer buffer = ByteBuffer.allocate(CAPACITY);

	private TCPConnectionImpl tcpConnection;

	@BeforeEach
	void init() {
		tcpConnection = new TCPConnectionImpl(socketChannel, buffer);
	}

	@Test
	void write() throws IOException {
		var bufferToWrite = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});
		when(socketChannel.write(bufferToWrite))
						.thenAnswer(invocation -> {
							bufferToWrite.position(bufferToWrite.position() + 1);
							return 1;
						});
		tcpConnection.write(bufferToWrite);
		verify(socketChannel, times(bufferToWrite.capacity())).write(bufferToWrite);
	}

	@Test
	void readGivenOnlyOneFunctionCallIsNeeded() throws IOException {
		var visited = new AtomicBoolean();
		tcpConnection.read(buf -> {
			if (visited.get()) {
				fail("Function should have been called only once!");
			}
			visited.set(true);
			return false;
		});
		verify(socketChannel).read(buffer);
	}

	@Test
	void readGivenMultipleFunctionCallIsNeeded() throws IOException {
		var counter = new AtomicInteger();
		int numInvocationsNeeded = 10;
		tcpConnection.read(buf -> counter.incrementAndGet() < numInvocationsNeeded);
		assertThat(counter).hasValue(numInvocationsNeeded);
		verify(socketChannel, times(numInvocationsNeeded)).read(buffer);
	}

	@Test
	void close() throws IOException {
		tcpConnection.close();
		verify(socketChannel).close();
	}

}