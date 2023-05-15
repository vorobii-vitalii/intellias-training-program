package tcp.server;

import java.util.SplittableRandom;
import java.util.function.Supplier;

public class RandomProvider<T> implements Supplier<T> {
	private final T[] arr;

	public RandomProvider(T... arr) {
		this.arr = arr;
	}

	@Override
	public T get() {
		var index = new SplittableRandom().nextInt(arr.length);
		return arr[index];
	}
}
