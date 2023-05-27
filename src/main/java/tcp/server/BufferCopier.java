package tcp.server;

import java.nio.ByteBuffer;

public class BufferCopier {
	private final ByteBufferPool byteBufferPool;

	public BufferCopier(ByteBufferPool byteBufferPool) {
		this.byteBufferPool = byteBufferPool;
	}

	public ByteBuffer copy(ByteBuffer byteBuffer) {
		int size = byteBuffer.limit();
		var copy = byteBufferPool.allocate(size);
		copy.put(byteBuffer);
		return copy;
	}
}
