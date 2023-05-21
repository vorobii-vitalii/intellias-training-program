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
		when(bufferContext.extract(anyInt(), anyInt())).thenAnswer(invocationOnMock -> {
			int start = invocationOnMock.getArgument(0);
			int end = invocationOnMock.getArgument(1);
			byte[] arr = new byte[end - start];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = bytes[start + i];
			}
			return arr;
		});
		return bufferContext;
	}

}
