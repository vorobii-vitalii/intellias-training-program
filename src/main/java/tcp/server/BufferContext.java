package tcp.server;

import net.jcip.annotations.NotThreadSafe;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@NotThreadSafe
public class BufferContext {
	// TODO: Make it configurable
	private static final int SINGLE_BUFFER_SIZE = 1536;
	private static final int NUM_BUFFERS_TO_KEEP_ON_RESET = 1;

	private final List<ByteBuffer> byteBuffers = new ArrayList<>();

	public ByteBuffer getAvailableBuffer() {
		addByteBufferIfNeeded();
		return byteBuffers.get(byteBuffers.size() - 1);
	}

	public int size() {
		if (byteBuffers.isEmpty()) {
			return 0;
		}
		return (byteBuffers.size() - 1) * SINGLE_BUFFER_SIZE + byteBuffers.get(byteBuffers.size() - 1).position();
	}

	public byte get(int pos) {
		validatePosition(pos);
		int bufferPos = pos / SINGLE_BUFFER_SIZE;
		return byteBuffers.get(bufferPos).get(pos % SINGLE_BUFFER_SIZE);
	}

	public void free(int bytesToFree) {
		if (bytesToFree == 0) {
			return;
		}
		var bytesToKeep = size() - bytesToFree;
		var firstBuffer = byteBuffers.get(0);
		for (int i = 0; i < bytesToKeep; i++) {
			firstBuffer.put(i, get(size() - bytesToKeep + i));
		}
		firstBuffer.position(bytesToKeep);
		while (byteBuffers.size() > NUM_BUFFERS_TO_KEEP_ON_RESET) {
			byteBuffers.remove(byteBuffers.size() - 1);
		}
	}

	private void validatePosition(int pos) {
		if (pos < 0 || pos >= size()) {
			throw new IllegalStateException("Position is not in bounds [0, " + size() + ")");
		}
	}

	private void addByteBufferIfNeeded() {
		if (byteBuffers.isEmpty() || isLastBufferFull()) {
			byteBuffers.add(ByteBuffer.allocateDirect(SINGLE_BUFFER_SIZE));
		}
	}

	private boolean isLastBufferFull() {
		ByteBuffer last = byteBuffers.get(byteBuffers.size() - 1);
		return last.position() == last.capacity();
	}

}
