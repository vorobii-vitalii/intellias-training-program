import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import echo.EchoAcceptOperationHandler;
import org.junit.jupiter.api.Test;

class TestEchoAcceptOperationHandler {

	public static final int BUFFER_CAPACITY = 250;

	@Test
	void testOperationHandlerGivenBufferCapacityIsNotPositive() {
		assertThrows(IllegalArgumentException.class, () -> new EchoAcceptOperationHandler(0));
	}

	@Test
	void testOperationHandler() throws IOException {
		// Given
		var echoAcceptOperationHandler = new EchoAcceptOperationHandler(BUFFER_CAPACITY);
		var serverSocketChannel = mock(ServerSocketChannel.class);
		var socketChannel = mock(SocketChannel.class);
		var selectionKey = mock(SelectionKey.class);
		var selector = mock(Selector.class);
		when(selectionKey.selector()).thenReturn(selector);
		when(selectionKey.channel()).thenReturn(serverSocketChannel);
		when(serverSocketChannel.accept()).thenReturn(socketChannel);
		// When
		echoAcceptOperationHandler.accept(selectionKey);
		// Then
		verify(socketChannel).configureBlocking(false);
		verify(socketChannel)
				.register(
						eq(selector),
						eq(SelectionKey.OP_READ),
						argThat(obj -> obj instanceof ByteBuffer && ((ByteBuffer) obj).capacity() == BUFFER_CAPACITY)
				);
	}

}