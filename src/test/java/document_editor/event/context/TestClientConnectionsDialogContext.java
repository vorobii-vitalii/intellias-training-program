package document_editor.event.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tcp.MessageSerializerImpl;
import tcp.server.BufferCopier;
import tcp.server.OperationType;
import tcp.server.SocketConnection;
import util.Serializable;

@ExtendWith(MockitoExtension.class)
class TestClientConnectionsDialogContext {

	public static final byte[] ARRAY = {1, 2, 3};
	public static final ByteBuffer BYTE_BUFFER = ByteBuffer.wrap(ARRAY);
	public static final int MAX_WAIT_MS = 10_000;
	@Mock
	Supplier<Instant> currentTimeProvider;

	@Mock
	MessageSerializerImpl messageSerializer;

	@Mock
	BufferCopier bufferCopier;

	ClientConnectionsContext clientConnectionsContext;


	@BeforeEach
	void init() {
		clientConnectionsContext = new ClientConnectionsContext(MAX_WAIT_MS, currentTimeProvider, messageSerializer, bufferCopier);
	}

	@Mock
	Serializable serializable;

	@Test
	void broadCastMessage() {
		var clientConnection1 = mock(SocketConnection.class);
		var clientConnection2 = mock(SocketConnection.class);

		when(currentTimeProvider.get()).thenReturn(Instant.now());

		clientConnectionsContext.addOrUpdateConnection(clientConnection1);
		clientConnectionsContext.addOrUpdateConnection(clientConnection2);

		when(messageSerializer.serialize(serializable)).thenReturn(BYTE_BUFFER);
		when(bufferCopier.copy(any())).thenReturn(ByteBuffer.wrap(ARRAY));

		clientConnectionsContext.broadCastMessage(serializable);

		verify(clientConnection1).appendResponse(argThat(v -> equal(v, ByteBuffer.wrap(ARRAY))));
		verify(clientConnection2).appendResponse(argThat(v -> equal(v, ByteBuffer.wrap(ARRAY))));
		verify(clientConnection1).changeOperation(OperationType.WRITE);
		verify(clientConnection2).changeOperation(OperationType.WRITE);
	}

	@Test
	void removeDisconnectedClients() throws IOException {
		var clientConnection = mock(SocketConnection.class);

		var now = Instant.now();
		when(currentTimeProvider.get()).thenReturn(now.minusSeconds(MAX_WAIT_MS - 1), now);

		clientConnectionsContext.addOrUpdateConnection(clientConnection);

		clientConnectionsContext.removeDisconnectedClients();

		verify(clientConnection).close();
	}

	private boolean equal(ByteBuffer byteBuffer1, ByteBuffer byteBuffer2) {
		return byteBuffer1.compareTo(byteBuffer2) == 0;
	}
}