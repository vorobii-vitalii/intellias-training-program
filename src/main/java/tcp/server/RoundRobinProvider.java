package tcp.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RoundRobinProvider<T> implements Supplier<T> {
	private final T[] arr;
	private final AtomicInteger atomicInteger = new AtomicInteger(0);

	public RoundRobinProvider(T... arr) {
		this.arr = arr;
	}

	@Override
	public T get() {
		var index = atomicInteger.getAndUpdate(i -> (i + 1) % arr.length);
		return arr[index];
	}
}
