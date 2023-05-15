package tcp.server;

import net.jcip.annotations.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@NotThreadSafe
public class BufferContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(BufferContext.class);
	// TODO: Make it configurable
	private static final int SINGLE_BUFFER_SIZE = 1536;

	private final List<ByteBuffer> byteBuffers = new ArrayList<>();
	private final ByteBufferPool byteBufferPool;

	public BufferContext(ByteBufferPool byteBufferPool) {
		this.byteBufferPool = byteBufferPool;
	}

	public ByteBuffer getAvailableBuffer() {
		addByteBufferIfNeeded();
		return byteBuffers.get(byteBuffers.size() - 1);
	}

	public int size() {
		var totalSize = 0;
		for (ByteBuffer byteBuffer : byteBuffers) {
			totalSize += byteBuffer.position();
		}
		return totalSize;
//		if (byteBuffers.isEmpty()) {
//			return 0;
//		}
//		return (byteBuffers.size() - 1) * SINGLE_BUFFER_SIZE + byteBuffers.get(byteBuffers.size() - 1).position();
	}

	public byte get(int pos) {
		validatePosition(pos);
		for (var buffer : byteBuffers) {
			if (pos < buffer.position()) {
				return buffer.get(pos);
			}
			pos -= buffer.position();
		}
		throw new IllegalStateException();
//		int bufferPos = pos / SINGLE_BUFFER_SIZE;
//		return byteBuffers.get(bufferPos).get(pos % SINGLE_BUFFER_SIZE);
	}

	public void free(int bytesToFree) {
		if (bytesToFree == 0) {
			return;
		}
		var currentSize = size();
		var bytesToKeep = currentSize - bytesToFree;
//		LOGGER.info("Current size = {}, bytesToKeep = {}", currentSize, bytesToKeep);
//
//
		ByteBuffer newBuffer = null;

		if (bytesToKeep > 0) {
			newBuffer = byteBufferPool.allocate(Math.max(bytesToKeep, SINGLE_BUFFER_SIZE));
			var lastBuffer = byteBuffers.get(byteBuffers.size() - 1);
			for (var i = 0; i < bytesToKeep; i++) {
				newBuffer.put(i, lastBuffer.get(lastBuffer.position() - bytesToKeep + i));
			}
			newBuffer.position(bytesToKeep);
		}
		while (!byteBuffers.isEmpty()) {
			byteBufferPool.save(byteBuffers.remove(byteBuffers.size() - 1));
		}
		if (bytesToKeep > 0) {
			byteBuffers.add(newBuffer);
		}

//		var firstBuffer = byteBuffers.get(0);
//		for (int i = 0; i < bytesToKeep; i++) {
//			firstBuffer.put(i, get(size() - bytesToKeep + i));
//		}
//		firstBuffer.position(bytesToKeep);
//		while (byteBuffers.size() > 1) {
//			var byteBuffer = byteBuffers.remove(byteBuffers.size() - 1);
//			byteBufferPool.save(byteBuffer);
//		}
	}

	private void validatePosition(int pos) {
		if (pos < 0 || pos >= size()) {
			throw new IllegalStateException("Position is not in bounds [0, " + size() + ")");
		}
	}

	private void addByteBufferIfNeeded() {
		if (byteBuffers.isEmpty() || isLastBufferFull()) {
			final ByteBuffer buffer = byteBufferPool.allocate(SINGLE_BUFFER_SIZE);
			LOGGER.debug("Allocated buffer {}", buffer);
			byteBuffers.add(buffer);
		}
	}

	private boolean isLastBufferFull() {
		ByteBuffer last = byteBuffers.get(byteBuffers.size() - 1);
		return last.position() == last.capacity();
	}

}
