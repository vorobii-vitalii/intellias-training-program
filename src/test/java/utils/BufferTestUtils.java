package utils;

import tcp.server.BufferContext;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BufferTestUtils {

	public static BufferContext createBufferContext(byte[] bytes) {
		var bufferContext = mock(BufferContext.class);
		when(bufferContext.size()).thenReturn(bytes.length);
		when(bufferContext.get(anyInt())).thenAnswer(invocation -> bytes[invocation.getArgument(0, Integer.class)]);
		return bufferContext;
	}

}
