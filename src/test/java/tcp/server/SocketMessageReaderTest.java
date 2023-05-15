package tcp.server;

import io.opentelemetry.api.trace.Span;
import tcp.server.reader.exception.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcp.server.reader.MessageReader;
import util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocketMessageReaderTest {
	private static final ByteBuffer BYTE_BUFFER = ByteBuffer.allocate(100);
	private static final int MESSAGE = 2;
	public static final int MESSAGE_BYTES = 50;

	@Mock
	MessageReader<Integer> messageReader;

	@InjectMocks
	SocketMessageReader<Integer> socketMessageReader;

	@Mock
	BufferContext bufferContext;

	@Mock
	ReadableByteChannel readableByteChannel;
	private Span requestSpan;

	@Test
	void readMessageGivenEnoughBytesWereRead() throws IOException, ParseException {
		when(bufferContext.getAvailableBuffer()).thenReturn(BYTE_BUFFER);
		when(readableByteChannel.read(BYTE_BUFFER)).thenReturn(20, 20, 30, 20, 0);
		when(messageReader.read(bufferContext, any()))
						.thenReturn(null)
						.thenReturn(null)
						.thenReturn(null)
						.thenReturn(null)
						.thenReturn(new Pair<>(MESSAGE, MESSAGE_BYTES));
		var readMessage = socketMessageReader.readMessage(bufferContext, readableByteChannel, requestSpan);
		assertThat(readMessage).isEqualTo(MESSAGE);
		verify(bufferContext).free(MESSAGE_BYTES);
		verify(readableByteChannel, times(4)).read(BYTE_BUFFER);
	}

	@Test
	void readMessageGivenNotEnoughBytesWereRead() throws IOException, ParseException {
		when(bufferContext.getAvailableBuffer()).thenReturn(BYTE_BUFFER);
		when(readableByteChannel.read(BYTE_BUFFER)).thenReturn(20, 20, 30, 20, 0);
		when(messageReader.read(bufferContext, any())).thenReturn(null);
		var readMessage = socketMessageReader.readMessage(bufferContext, readableByteChannel, requestSpan);
		assertThat(readMessage).isNull();
		verify(bufferContext, never()).free(anyInt());
		verify(readableByteChannel, times(5)).read(BYTE_BUFFER);
	}

}