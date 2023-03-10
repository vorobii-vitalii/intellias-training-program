import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import echo.EchoWriteOperationHandler;
import org.junit.jupiter.api.Test;

class TestEchoWriteOperationHandler {
	EchoWriteOperationHandler echoWriteOperationHandler = new EchoWriteOperationHandler();

	@Test
	void testOperationHandlerGivenWholeBufferHasBeenWritten() throws IOException {
		var selectionKey = mock(SelectionKey.class);
		var socketChannel = mock(SocketChannel.class);
		var byteBuffer = ByteBuffer.allocate(1000);
		when(selectionKey.channel()).thenReturn(socketChannel);
		when(selectionKey.attachment()).thenReturn(byteBuffer);
		when(socketChannel.write(byteBuffer)).thenAnswer(invocation -> {
			byteBuffer.position(byteBuffer.capacity());
			return byteBuffer.capacity();
		});
		echoWriteOperationHandler.accept(selectionKey);
		verify(selectionKey).interestOps(SelectionKey.OP_READ);
	}

	@Test
	void testOperationHandlerGivenPartOfBufferWasWritten() throws IOException {
		var selectionKey = mock(SelectionKey.class);
		var socketChannel = mock(SocketChannel.class);
		var byteBuffer = ByteBuffer.allocate(1000);
		when(selectionKey.channel()).thenReturn(socketChannel);
		when(selectionKey.attachment()).thenReturn(byteBuffer);
		when(socketChannel.write(byteBuffer)).thenAnswer(invocation -> {
			byteBuffer.position(byteBuffer.capacity() / 2);
			return byteBuffer.capacity() / 2;
		});
		echoWriteOperationHandler.accept(selectionKey);
		verify(selectionKey, never()).interestOps(SelectionKey.OP_READ);
	}

}
