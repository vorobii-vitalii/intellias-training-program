package tcp.server;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RoundRobinProvider<T> implements Supplier<T> {
	private final List<T> arr;
	private final AtomicInteger atomicInteger = new AtomicInteger(0);

	public RoundRobinProvider(T... arr) {
		this.arr = Arrays.asList(arr);
	}

	public RoundRobinProvider(List<T> list) {
		this.arr = list;
	}

	@Override
	public T get() {
		var index = atomicInteger.getAndUpdate(i -> (i + 1) % arr.size());
		return arr.get(index);
	}
}
