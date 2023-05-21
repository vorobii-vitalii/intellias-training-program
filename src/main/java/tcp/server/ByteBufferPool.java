package tcp.server;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class ByteBufferPool {
	private final ConcurrentSkipListMap<Integer, ConcurrentLinkedDeque<ByteBuffer>> byteBuffersBySize = new ConcurrentSkipListMap<>();
	private final Function<Integer, ByteBuffer> allocator;

	public ByteBufferPool(Function<Integer, ByteBuffer> allocator) {
		this.allocator = allocator;
	}

	public ByteBuffer allocate(int size) {
		while (true) {
			var entry = byteBuffersBySize.ceilingEntry(size);
			if (entry == null) {
				break;
			}
			var queue = entry.getValue();
			if (isEmpty(queue)) {
				byteBuffersBySize.compute(entry.getKey(), (k, v) -> {
					if (isEmpty(v)) {
						return null;
					}
					return v;
				});
			} else {
				var buffer = queue.poll();
				if (buffer != null) {
					return buffer;
				}
			}
		}
		return allocator.apply(size);
	}

	private boolean isEmpty(ConcurrentLinkedDeque<?> deque) {
		return deque == null || deque.isEmpty();
	}

	public void save(ByteBuffer buffer) {
		var size = buffer.capacity();
		buffer.clear();
		byteBuffersBySize.compute(size, (s, arr) -> {
			if (arr != null) {
				arr.add(buffer);
				return arr;
			}
			var dequeue = new ConcurrentLinkedDeque<ByteBuffer>();
			dequeue.add(buffer);
			return dequeue;
		});
	}

}
