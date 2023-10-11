package tcp.server;

import net.jcip.annotations.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@NotThreadSafe
public class BufferContext implements BytesSource {
	private static final int SINGLE_BUFFER_SIZE = 8192;

	private final List<ByteBuffer> byteBuffers = new ArrayList<>();
	private final List<Integer> list = new ArrayList<>();
	private final ByteBufferPool byteBufferPool;

	public BufferContext(ByteBufferPool byteBufferPool) {
		this.byteBufferPool = byteBufferPool;
	}

	public ByteBuffer getAvailableBuffer() {
		addByteBufferIfNeeded();
		return byteBuffers.get(byteBuffers.size() - 1);
	}

	public byte[] extract(int from, int end) {
		if (from == end) {
			return new byte[] {};
		}
		if (from > end) {
			throw new IllegalStateException("from = " + from + " > end = " + end);
		}
		validatePosition(from);
		int count = end - from;
		var res = new byte[count];
		var index = getBufferIndex(from);
		while (count > 0) {
			int pos = from + res.length - count;
			var buffer = byteBuffers.get(index);
			var bufferStart = pos - list.get(index);
			var bytesToRead = Math.min(count, buffer.position() - bufferStart);
			buffer.get(bufferStart, res, res.length - count, bytesToRead);
			index++;
			count -= bytesToRead;
		}
		return res;
	}

	public int size() {
		if (list.isEmpty()) {
			return 0;
		}
		return list.get(list.size() - 1) + byteBuffers.get(list.size() - 1).position();
	}

	public void write(byte[] bytes) {
		int index = 0;
		while (index < bytes.length) {
			addByteBufferIfNeeded();
			var buffer = byteBuffers.get(byteBuffers.size() - 1);
			int prev = buffer.position();
			buffer.put(bytes, index, Math.min(bytes.length - index, buffer.remaining()));
			index += buffer.position() - prev;
		}
	}

	public byte get(int pos) {
		validatePosition(pos);
		var index = getBufferIndex(pos);
		return byteBuffers.get(index).get(pos - list.get(index));
	}

	private int getBufferIndex(int pos) {
		var low = 0;
		var high = byteBuffers.size() - 1;
		while (low <= high) {
			int mid = low + (high - low) / 2;
			int start = list.get(mid);
			int end = start + byteBuffers.get(mid).position();
			if (pos < start) {
				high = mid - 1;
			}
			else if (pos >= end) {
				low = mid + 1;
			}
			else {
				return mid;
			}
		}
		throw new IllegalStateException();
	}

	public void free(int bytesToFree) {
		if (bytesToFree == 0) {
			return;
		}
		var currentSize = size();
		var bytesToKeep = currentSize - bytesToFree;
		ByteBuffer newBuffer = null;

		if (bytesToKeep > 0) {
			newBuffer = byteBufferPool.allocate(Math.max(bytesToKeep, SINGLE_BUFFER_SIZE));
			for (var i = 0; i < bytesToKeep; i++) {
				newBuffer.put(i, get(currentSize - bytesToKeep + i));
			}
			newBuffer.position(bytesToKeep);
		}
		while (!byteBuffers.isEmpty()) {
			byteBufferPool.save(byteBuffers.remove(byteBuffers.size() - 1));
			list.remove(list.size() - 1);
		}
		if (bytesToKeep > 0) {
			byteBuffers.add(newBuffer);
			list.add(0);
		}
	}

	private void validatePosition(int pos) {
		if (pos < 0 || pos >= size()) {
			throw new IllegalStateException("Position is not in bounds [0, " + size() + ")" + " -> " + pos);
		}
	}

	private void addByteBufferIfNeeded() {
		if (byteBuffers.isEmpty() || isLastBufferFull()) {
			var buffer = byteBufferPool.allocate(SINGLE_BUFFER_SIZE);
			list.add(list.isEmpty() ? 0 : (list.get(list.size() - 1) + byteBuffers.get(list.size() - 1).capacity()));
			byteBuffers.add(buffer);
		}
	}

	private boolean isLastBufferFull() {
		ByteBuffer last = byteBuffers.get(byteBuffers.size() - 1);
		return last.position() == last.capacity();
	}

}
