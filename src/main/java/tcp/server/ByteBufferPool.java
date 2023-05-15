package tcp.server;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.jcip.annotations.ThreadSafe;

@ThreadSafe
public class ByteBufferPool {
	private final ConcurrentSkipListMap<Integer, ConcurrentLinkedDeque<ByteBuffer>> byteBuffersBySize = new ConcurrentSkipListMap<>();
//private final ConcurrentHashMap<Integer, ConcurrentLinkedDeque<ByteBuffer>> byteBuffersBySize = new ConcurrentHashMap<>();
	private final Function<Integer, ByteBuffer> allocator;

	public ByteBufferPool(Function<Integer, ByteBuffer> allocator) {
		this.allocator = allocator;
	}

	public synchronized ByteBuffer allocate(int size) {
		while (true) {
			var availableBufferSize = byteBuffersBySize.ceilingKey(size);
			if (availableBufferSize == null) {
				break;
			}
			var reference = new AtomicReference<ByteBuffer>();
			byteBuffersBySize.computeIfPresent(availableBufferSize, (s, queue) -> {
				if (isEmpty(queue)) {
					return null;
				}
				var buffer = queue.poll();
				reference.set(buffer);
				return queue;
			});
			if (reference.get() != null) {
				return reference.get();
			}
		}
		return allocator.apply(size);
	}

	private boolean isEmpty(ConcurrentLinkedDeque<?> deque) {
		return deque == null || deque.isEmpty();
	}

	public synchronized void save(ByteBuffer buffer) {
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
