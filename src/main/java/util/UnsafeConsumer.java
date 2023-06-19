package util;

public interface UnsafeConsumer<T> {
	void accept(T obj) throws Exception;
}
