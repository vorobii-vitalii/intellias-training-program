import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import echo.EchoReadOperationHandler;
import org.junit.jupiter.api.Test;

class TestEchoReadOperationHandler {
	EchoReadOperationHandler echoReadOperationHandler = new EchoReadOperationHandler();

	@Test
	void testOperationHandler() throws IOException {
		var selectionKey = mock(SelectionKey.class);
		var socketChannel = mock(SocketChannel.class);
		var byteBuffer = ByteBuffer.allocate(1000);
		when(selectionKey.channel()).thenReturn(socketChannel);
		when(selectionKey.attachment()).thenReturn(byteBuffer);
		byte[] bytes = new byte[] {1, 2, 3, 4, 5, 6, 7};
		when(socketChannel.read(byteBuffer)).thenAnswer(invocation -> {
			byteBuffer.put(bytes);
			return bytes.length;
		});
		echoReadOperationHandler.accept(selectionKey);
		byte[] readBytes = new byte[bytes.length];
		byteBuffer.get(readBytes);
		assertThat(readBytes).isEqualTo(bytes);
	}
}
